import java.sql.*;
import javax.servlet.http.HttpServletRequest;
class ParameterizedSafe {
  void run(HttpServletRequest request, Connection connection) throws Exception {
    String user = request.getParameter("id");
    PreparedStatement statement = connection.prepareStatement("SELECT * FROM users WHERE id=?");
    statement.setString(1, user);
    statement.executeQuery();
  }
}
