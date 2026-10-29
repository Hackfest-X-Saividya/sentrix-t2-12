package ai.zerotrace.zeroremediate;

import ai.zerotrace.zeroremediate.model.*;
import ai.zerotrace.zeroremediate.api.*;
import ai.zerotrace.zeroremediate.provider.FixtureRemediationProvider;
import ai.zerotrace.zeroremediate.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class RemediationApiTest {
    MockMvc mvc; ObjectMapper mapper;
    @BeforeEach void setUp(){mapper=new ObjectMapper();var store=new FindingStore();var provider=new FixtureRemediationProvider();var validator=new ProposalValidator();var runner=new VerificationRunner(store,new TrustedVerificationSuite.FixtureSqlInjectionSuite(),1000);var service=new FindingService(store,provider,validator,runner);var beanValidator=new LocalValidatorFactoryBean();beanValidator.afterPropertiesSet();mvc=MockMvcBuilders.standaloneSetup(new RemediationController(service)).setControllerAdvice(new ApiExceptionHandler()).setValidator(beanValidator).addFilters(new PayloadLimitFilter()).build();}
    private FindingSubmission finding(String id,String code){return new FindingSubmission(id,"zerotrace.java.sql-injection","sql-injection","HIGH","src/UserDao.java",12,13,java.util.List.of("tainted input reaches SQL"),"Potential SQL injection",code);}
    @Test void fullFixtureWorkflowReturnsExplicitEvidenceAndLeavesFixturesUnchanged() throws Exception {
        byte[] beforeV=getClass().getResourceAsStream("/fixtures/vulnerable-sqli.java.txt").readAllBytes();byte[] beforeS=getClass().getResourceAsStream("/fixtures/safe-sqli.java.txt").readAllBytes();
        mvc.perform(post("/api/v1/findings").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(finding("finding-demo",FixtureRemediationProvider.VULNERABLE))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.findingId").value("finding-demo"));
        mvc.perform(get("/api/v1/findings/finding-demo")).andExpect(status().isOk()).andExpect(jsonPath("$.filePath").value("src/UserDao.java"));
        var proposalResult=mvc.perform(post("/api/v1/findings/finding-demo/proposals")).andReturn().getResponse();assertEquals(201,proposalResult.getStatus(),proposalResult.getContentAsString());String proposal=proposalResult.getContentAsString();assertTrue(proposal.contains("FIXTURE"));assertTrue(proposal.contains("PreparedStatement"));
        String id=mapper.readTree(proposal).get("proposalId").asText();
        mvc.perform(get("/api/v1/proposals/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.proposalId").value(id));
        var verification=mvc.perform(post("/api/v1/proposals/"+id+"/verify")).andExpect(status().isCreated()).andExpect(jsonPath("$.outcome").value("VERIFIED")).andExpect(jsonPath("$.checks.length()").value(2)).andReturn().getResponse().getContentAsString();
        String verificationId=mapper.readTree(verification).get("verificationId").asText();mvc.perform(get("/api/v1/verifications/"+verificationId)).andExpect(status().isOk()).andExpect(jsonPath("$.verificationId").value(verificationId));
        assertArrayEquals(beforeV,getClass().getResourceAsStream("/fixtures/vulnerable-sqli.java.txt").readAllBytes());assertArrayEquals(beforeS,getClass().getResourceAsStream("/fixtures/safe-sqli.java.txt").readAllBytes());
    }
    @Test void rejectsMissingFieldsAndMalformedJson() throws Exception {
        mvc.perform(post("/api/v1/findings").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/findings").contentType(MediaType.APPLICATION_JSON).content("{broken" )).andExpect(status().isBadRequest());
    }
    @Test void rejectsUnsupportedAndUnsafePaths() throws Exception {
        var invalid=finding("bad-path","valid context");invalid=new FindingSubmission(invalid.findingId(),invalid.ruleId(),invalid.category(),invalid.severity(),"../secret",invalid.lineStart(),invalid.lineEnd(),invalid.evidence(),invalid.explanation(),invalid.codeContext());
        mvc.perform(post("/api/v1/findings").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(invalid)))
            .andExpect(status().isBadRequest());
    }
    @Test void rejectsOversizedContentLength() throws Exception {mvc.perform(post("/api/v1/findings").contentType(MediaType.APPLICATION_JSON).content(new byte[16*1024+1])).andExpect(status().isPayloadTooLarge());}
    @Test void unknownFindingDoesNotGenerateProposal() throws Exception {var response=mvc.perform(post("/api/v1/findings/missing/proposals")).andReturn().getResponse();assertEquals(404,response.getStatus(),response.getContentAsString());}
}
