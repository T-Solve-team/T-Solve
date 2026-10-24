package vn.tsolve.review.manual;

import org.springframework.boot.SpringApplication;
import vn.tsolve.TSolveApplication;

/** Local manual-test entry point. Test classes are excluded from the packaged application. */
public class ManualReviewApplication {
    public static void main(String[] args) {
        SpringApplication.from(TSolveApplication::main)
                .with(ManualReviewConfiguration.class)
                .run(args);
    }
}
