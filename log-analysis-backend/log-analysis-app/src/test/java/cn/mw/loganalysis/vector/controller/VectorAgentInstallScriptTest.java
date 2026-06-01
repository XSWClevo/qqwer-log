package cn.mw.loganalysis.vector.controller;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class VectorAgentInstallScriptTest {

    @Test
    void installScriptShouldKeepAgentConfigOutsideVectorConfigDir() throws Exception {
        ClassPathResource resource = new ClassPathResource("scripts/install-agent.sh");
        String script = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(script).contains("cat > ${INSTALL_DIR}/agent.yaml <<EOF");
        assertThat(script).doesNotContain("cat > ${CONFIG_DIR}/agent.yaml <<EOF");
        assertThat(script).contains("ExecStart=${BIN_DIR}/vector-agent -config ${INSTALL_DIR}/agent.yaml");
        assertThat(script).contains("ExecStart=${BIN_DIR}/vector --config-dir ${CONFIG_DIR} --watch-config");
    }
}
