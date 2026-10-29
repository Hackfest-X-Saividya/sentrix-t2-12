import java.sql.Statement;
import javax.servlet.http.HttpServletRequest;

class CustomWrapperReview {
    void query(HttpServletRequest request, Statement statement) throws Exception {
        String input = request.getParameter("id");
        String sql = buildQuery(input);
        statement.executeQuery(sql);
    }

    private String buildQuery(String input) {
        return "SELECT * FROM users WHERE id = " + input;
    }
}