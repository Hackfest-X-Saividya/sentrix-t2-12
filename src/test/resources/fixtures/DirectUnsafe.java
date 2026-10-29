import java.sql.*;
import javax.servlet.http.HttpServletRequest;
class DirectUnsafe {
  void run(HttpServletRequest request, Statement statement) throws Exception {
    String user = request.getParameter("id");
    String sql = "SELECT * FROM users WHERE id='" + user + "'";
    statement.executeQuery(sql);
  }
}
