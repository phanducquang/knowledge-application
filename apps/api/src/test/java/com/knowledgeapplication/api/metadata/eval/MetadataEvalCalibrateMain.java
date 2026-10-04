package com.knowledgeapplication.api.metadata.eval;

import java.nio.file.Path;

/** Explicit local synthetic baseline input; no live fallback or application configuration loading. */
public final class MetadataEvalCalibrateMain {
    private MetadataEvalCalibrateMain() {}
    public static void main(String[] args) {
        try {
            String path=System.getenv("METADATA_EVAL_BASELINE_REPORT");
            if(path==null || path.isBlank()) throw new IllegalArgumentException();
            var source=Path.of(path); var frozen=MetadataCalibration.load(source);
            var report=MetadataCalibration.evaluate(frozen,MetadataCalibrationCorpus.load());
            var directory=Path.of(System.getProperty("metadata.eval.report-directory","build/reports/metadata-eval"));
            MetadataCalibration.write(report,directory,source);
            System.out.println("Offline calibration COMPLETE: 20 unchanged synthetic outputs, metadata-eval-v1 versus metadata-eval-v2; zero provider calls/reservations. Human review pending.");
        } catch(Exception ex) {
            // No path, credentials, provider body, arbitrary exception message/cause or live fallback.
            System.err.println("Offline calibration failed: missing/invalid completed synthetic baseline or calibration configuration. Zero provider calls.");
            System.exit(1);
        }
    }
}
