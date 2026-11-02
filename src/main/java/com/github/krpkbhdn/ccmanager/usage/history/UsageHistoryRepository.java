package com.github.krpkbhdn.ccmanager.usage.history;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalDouble;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

/** SQLite storage of usage samples; old raw samples are folded into per-day totals by {@link #aggregateBefore}. */
@Repository
public class UsageHistoryRepository {

    private static final String LOCAL_DAY = "date(at / 1000, 'unixepoch', 'localtime')";
    private static final String NOT_BASELINE = "id NOT IN (SELECT MAX(id) FROM usage_sample WHERE claude_session_id IS NOT NULL "
            + "GROUP BY claude_session_id)";
    private static final String SESSION_KEY = "COALESCE(claude_session_id, ccm_session_id)";

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    public UsageHistoryRepository(JdbcClient jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public void insertSample(UsageSample s) {
        jdbc.sql("""
                INSERT INTO usage_sample (at, ccm_session_id, claude_session_id, project_id, model, effort,
                    context_percent, cost_usd, cost_delta) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(s.at().toEpochMilli(), s.ccmSessionId(), s.claudeSessionId(), s.projectId(), s.model(),
                        s.effort(), s.contextPercent(), s.costUsd(), s.costDelta())
                .update();
    }

    /** Latest cumulative cost reported for the Claude session, the baseline of the next delta. */
    public OptionalDouble lastCost(String claudeSessionId) {
        return jdbc.sql("SELECT cost_usd FROM usage_sample WHERE claude_session_id = ? ORDER BY at DESC, id DESC LIMIT 1")
                .param(claudeSessionId).query(Double.class).optional()
                .map(OptionalDouble::of).orElseGet(OptionalDouble::empty);
    }

    public void insertRateLimit(RateLimitSample s) {
        jdbc.sql("""
                INSERT INTO rate_limit_sample (at, five_hour_pct, five_hour_resets_at, seven_day_pct,
                    seven_day_resets_at) VALUES (?, ?, ?, ?, ?)""")
                .params(s.at().toEpochMilli(), s.fiveHourPercent(), millis(s.fiveHourResetsAt()), s.sevenDayPercent(),
                        millis(s.sevenDayResetsAt()))
                .update();
    }

    /** Costs per local day, project and model in [from, to), raw samples and aggregated days merged. */
    public List<DailyCost> dailyCosts(Instant from, Instant to) {
        return jdbc.sql("""
                SELECT day, project_id, model, SUM(cost) AS cost FROM (
                    SELECT %s AS day, project_id, COALESCE(model, '') AS model, cost_delta AS cost
                    FROM usage_sample WHERE at >= :from AND at < :to
                    UNION ALL
                    SELECT day, project_id, model, cost_usd FROM usage_daily
                    WHERE day >= %s AND day <= %s)
                GROUP BY day, project_id, model ORDER BY day, project_id, model"""
                .formatted(LOCAL_DAY, dayOf(":from"), dayOf(":to - 1")))
                .param("from", from.toEpochMilli()).param("to", to.toEpochMilli())
                .query((rs, i) -> new DailyCost(LocalDate.parse(rs.getString("day")), rs.getLong("project_id"),
                        rs.getString("model"), rs.getDouble("cost")))
                .list();
    }

    /** Most expensive sessions in [from, to); reads raw samples only, so already aggregated days are not covered. */
    public List<SessionCost> topSessions(Instant from, Instant to, int limit) {
        return jdbc.sql("""
                SELECT claude_session_id, MIN(ccm_session_id) AS ccm_session_id, MIN(project_id) AS project_id,
                    COALESCE(MAX(model), '') AS model, SUM(cost_delta) AS cost
                FROM usage_sample WHERE at >= ? AND at < ?
                GROUP BY %s ORDER BY cost DESC, %s LIMIT ?""".formatted(SESSION_KEY, SESSION_KEY))
                .params(from.toEpochMilli(), to.toEpochMilli(), limit)
                .query((rs, i) -> new SessionCost(rs.getString("claude_session_id"), rs.getString("ccm_session_id"),
                        rs.getLong("project_id"), rs.getString("model"), rs.getDouble("cost")))
                .list();
    }

    public PeriodSummary summary(Instant from, Instant to) {
        var raw = jdbc.sql("""
                SELECT COALESCE(SUM(cost_delta), 0), COUNT(DISTINCT %s), COUNT(*) FROM usage_sample
                WHERE at >= :from AND at < :to""".formatted(SESSION_KEY))
                .param("from", from.toEpochMilli()).param("to", to.toEpochMilli())
                .query((rs, i) -> new PeriodSummary(rs.getDouble(1), rs.getLong(2), rs.getLong(3))).single();
        // sessions spanning several days are counted once per day
        var daily = jdbc.sql("""
                SELECT COALESCE(SUM(cost_usd), 0), COALESCE(SUM(sessions), 0), COALESCE(SUM(samples), 0)
                FROM usage_daily WHERE day >= %s AND day <= %s""".formatted(dayOf(":from"), dayOf(":to - 1")))
                .param("from", from.toEpochMilli()).param("to", to.toEpochMilli())
                .query((rs, i) -> new PeriodSummary(rs.getDouble(1), rs.getLong(2), rs.getLong(3))).single();
        return new PeriodSummary(raw.totalCostUsd() + daily.totalCostUsd(), raw.sessionCount() + daily.sessionCount(),
                raw.sampleCount() + daily.sampleCount());
    }

    public double costOn(LocalDate day) {
        var zone = ZoneId.systemDefault();
        return jdbc.sql("""
                SELECT (SELECT COALESCE(SUM(cost_delta), 0) FROM usage_sample WHERE at >= :from AND at < :to)
                     + (SELECT COALESCE(SUM(cost_usd), 0) FROM usage_daily WHERE day = :day)""")
                .param("from", day.atStartOfDay(zone).toInstant().toEpochMilli())
                .param("to", day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                .param("day", day.toString()).query(Double.class).single();
    }

    /** True only for the caller that recorded the alert first. */
    public boolean markAlertFired(String key, long firedAt) {
        return jdbc.sql("INSERT OR IGNORE INTO usage_alert (alert_key, fired_at) VALUES (?, ?)")
                .params(key, firedAt).update() > 0;
    }

    /**
     * Folds samples of whole local days before the cutoff's day into usage_daily and drops them; totals stay the same.
     * The newest sample of each Claude session stays as the baseline of its next cost delta.
     */
    public void aggregateBefore(long cutoffMillis) {
        var zone = ZoneId.systemDefault();
        var dayStart = Instant.ofEpochMilli(cutoffMillis).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
                .toEpochMilli();
        transaction.executeWithoutResult(status -> {
            jdbc.sql("""
                    INSERT INTO usage_daily (day, project_id, model, cost_usd, sessions, samples)
                    SELECT %s, project_id, COALESCE(model, ''), SUM(cost_delta), COUNT(DISTINCT %s), COUNT(*)
                    FROM usage_sample WHERE at < ? AND %s GROUP BY 1, 2, 3
                    ON CONFLICT (day, project_id, model) DO UPDATE SET
                        cost_usd = cost_usd + excluded.cost_usd,
                        sessions = sessions + excluded.sessions,
                        samples = samples + excluded.samples""".formatted(LOCAL_DAY, SESSION_KEY, NOT_BASELINE))
                    .param(dayStart).update();
            jdbc.sql("DELETE FROM usage_sample WHERE at < ? AND " + NOT_BASELINE).param(dayStart).update();
            jdbc.sql("DELETE FROM rate_limit_sample WHERE at < ?").param(dayStart).update();
            jdbc.sql("DELETE FROM usage_alert WHERE fired_at < ?").param(dayStart).update();
        });
    }

    private static String dayOf(String millisExpression) {
        return "date((" + millisExpression + ") / 1000, 'unixepoch', 'localtime')";
    }

    private static Long millis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
