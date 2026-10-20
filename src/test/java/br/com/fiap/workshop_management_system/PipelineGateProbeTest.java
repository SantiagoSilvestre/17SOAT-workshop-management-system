package br.com.fiap.workshop_management_system;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

// TEMPORARY: fails on purpose to prove that the CD pipeline stops at build-test (CP6 negative check).
// Reverted right after the run; never merge this file.
class PipelineGateProbeTest {

    @Test
    void failsOnPurposeToBlockImageAndDeployJobs() {
        fail("Intentional failure: image and deploy-kind must be skipped");
    }
}
