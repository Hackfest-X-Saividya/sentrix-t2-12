import java.sql.*;
import javax.servlet.http.HttpServletRequest;
class IndirectSameMethod {
  String decorate(String value) { return "SELECT * FROM users WHERE id='" + value + "'"; }
  void run(HttpServletRequest request, Statement statement) throws Exception {
    String user = request.getParameter("id");
    String sql = decorate(user);
    statement.executeQuery(sql);
  }
}
