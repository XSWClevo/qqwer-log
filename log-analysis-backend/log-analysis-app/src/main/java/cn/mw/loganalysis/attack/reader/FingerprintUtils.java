package cn.mw.loganalysis.attack.reader;

import cn.mw.loganalysis.attack.entity.AttackLogDataset;
import cn.mw.loganalysis.common.util.DateTimeUtils;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

public final class FingerprintUtils {

    private FingerprintUtils() {
    }

    public static String logFingerprint(AttackLogDataset dataset,
                                        LocalDateTime timestamp,
                                        String sourceIp,
                                        String hostname,
                                        String message,
                                        String raw) {
        String payload = String.join("|",
                StringUtils.defaultString(dataset.getDatasourceType()),
                StringUtils.defaultString(dataset.getDatasourceId()),
                StringUtils.defaultString(dataset.getDatabaseName()),
                StringUtils.defaultString(dataset.getTableName()),
                StringUtils.defaultString(dataset.getIndexName()),
                StringUtils.defaultString(DateTimeUtils.formatWithMillis(timestamp)),
                StringUtils.defaultString(sourceIp),
                StringUtils.defaultString(hostname),
                StringUtils.defaultIfBlank(raw, message));
        return sha256(payload);
    }

    public static String classificationKey(AttackLogDataset dataset, String fingerprint, String ruleId) {
        String payload = String.join("|",
                StringUtils.defaultString(dataset.getDatasourceType()),
                StringUtils.defaultString(dataset.getDatasourceId()),
                StringUtils.defaultString(dataset.getDatabaseName()),
                StringUtils.defaultString(dataset.getTableName()),
                StringUtils.defaultString(dataset.getIndexName()),
                StringUtils.defaultString(fingerprint),
                StringUtils.defaultString(ruleId));
        return sha256(payload);
    }

    private static String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("生成指纹失败", ex);
        }
    }
}
