package ai.zerotrace.zeroremediate.api;

import ai.zerotrace.zeroremediate.model.*;
import ai.zerotrace.zeroremediate.service.FindingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1")
public class RemediationController {
    private final FindingService service;
    public RemediationController(FindingService service){this.service=service;}
    @PostMapping("/findings") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public SecurityFinding submit(@Valid @RequestBody FindingSubmission in){return service.submit(in);}
    @GetMapping("/findings/{id}") public SecurityFinding finding(@PathVariable("id") String id){return service.store().finding(id);}
    @PostMapping("/findings/{id}/proposals") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public RemediationProposal propose(@PathVariable("id") String id){return service.propose(id);}
    @GetMapping("/proposals/{id}") public RemediationProposal proposal(@PathVariable("id") String id){return service.store().proposal(id);}
    @PostMapping("/proposals/{id}/verify") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public VerificationReport verify(@PathVariable("id") String id){return service.verify(id);}
    @GetMapping("/verifications/{id}") public VerificationReport report(@PathVariable("id") String id){return service.store().report(id);}
    @GetMapping("/health") public java.util.Map<String,String> health(){return java.util.Map.of("status","UP","providerMode",service.providerMode());}
}
