package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.entity.VectorPackage;
import cn.mw.loganalysis.vector.mapper.VectorPackageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VectorPackageServicePlatformTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldNormalizePlatformIdentifiers() {
        VectorPackageMapper packageMapper = mock(VectorPackageMapper.class);
        VectorPackageService service = new VectorPackageService(packageMapper);

        assertThat(service.normalizeOsType("macos")).isEqualTo("darwin");
        assertThat(service.normalizeOsType("linux")).isEqualTo("linux");
        assertThat(service.normalizeOsType(null)).isEqualTo("linux");

        assertThat(service.normalizeArch("x86_64")).isEqualTo("amd64");
        assertThat(service.normalizeArch("amd64")).isEqualTo("amd64");
        assertThat(service.normalizeArch("aarch64")).isEqualTo("arm64");
        assertThat(service.normalizeArch("arm64")).isEqualTo("arm64");
        assertThat(service.normalizeArch(null)).isEqualTo("amd64");
    }

    @Test
    void shouldPersistNormalizedPlatformWhenUploadingPackage() throws Exception {
        VectorPackageMapper packageMapper = mock(VectorPackageMapper.class);
        VectorPackageService service = new VectorPackageService(packageMapper);
        ReflectionTestUtils.setField(service, "storagePath", tempDir.toString());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "vector-agent-bundle.tar.gz",
                "application/gzip",
                "bundle-content".getBytes()
        );

        service.uploadPackage(file, "vector-agent-bundle", "1.0.0", "macos", "x86_64", "test", "tester");

        ArgumentCaptor<VectorPackage> pkgCaptor = ArgumentCaptor.forClass(VectorPackage.class);
        verify(packageMapper).insert(pkgCaptor.capture());

        VectorPackage pkg = pkgCaptor.getValue();
        assertThat(pkg.getOsType()).isEqualTo("darwin");
        assertThat(pkg.getArch()).isEqualTo("amd64");
        assertThat(pkg.getDownloadPath()).contains("vector-agent-bundle/darwin/amd64");

        verify(packageMapper).update(any(), any());
    }
}
