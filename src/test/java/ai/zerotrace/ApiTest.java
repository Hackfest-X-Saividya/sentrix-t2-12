package ai.zerotrace;

import ai.zerotrace.analysis.AnalysisEngine;
import ai.zerotrace.api.ScanController;
import ai.zerotrace.ingest.ProjectIngestor;
import ai.zerotrace.service.ScanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScanController.class)
@Import(ScanService.class)
class ApiTest {
    @Autowired MockMvc mvc;
    @MockBean AnalysisEngine engine;
    @MockBean ProjectIngestor ingestor;
    @Test void healthSucceeds() throws Exception { mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.phase").value("1")); }
    @Test void unknownScanIsNotFound() throws Exception { mvc.perform(get("/api/v1/scans/not-real")).andExpect(status().isNotFound()); }
    @Test void unsupportedUploadGetsStructuredError() throws Exception {
        mvc.perform(multipart("/api/v1/scans").file(new org.springframework.mock.web.MockMultipartFile("file", "source.tar", "application/octet-stream", new byte[]{1})))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("analysis_error"));
    }
}
