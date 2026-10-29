package ai.zerotrace.zeroremediate.provider;

import ai.zerotrace.zeroremediate.model.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@ConditionalOnProperty(name="zeroremediate.provider",havingValue="fixture",matchIfMissing=true)
public class FixtureRemediationProvider implements RemediationProvider {
    public static final String VULNERABLE="String sql = \"SELECT * FROM users WHERE name = '\" + userInput + \"'\";\nstatement.executeQuery(sql);";
    public static final String SAFE="String sql = \"SELECT * FROM users WHERE name = ?\";\nPreparedStatement statement = connection.prepareStatement(sql);\nstatement.setString(1, userInput);\nstatement.executeQuery();";
    @Override public String mode(){return "FIXTURE";}
    @Override public ProposalDraft propose(SecurityFinding f) {
        if(!f.ruleId().equals("zerotrace.java.sql-injection") || !f.category().equalsIgnoreCase("sql-injection") || !f.codeContext().strip().equals(VULNERABLE)) throw new IllegalArgumentException("Fixture provider supports only its exact synthetic SQL injection fixture");
        String diff="--- a/"+f.filePath()+"\n+++ b/"+f.filePath()+"\n@@ -1,2 +1,4 @@\n-"+VULNERABLE.replace("\n","\n-")+"\n+"+SAFE.replace("\n","\n+");
        return new ProposalDraft("Untrusted input is concatenated into SQL syntax.","Bind the input as a query parameter so it is treated as data.",SAFE,diff,"PreparedStatement placeholders separate SQL structure from user-controlled values.",List.of("Synthetic fixture only; no repository file was read or changed."),"Assert the SQL uses a placeholder and binds userInput with setString.");
    }
}
