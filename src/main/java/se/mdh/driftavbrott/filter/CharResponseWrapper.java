package se.mdh.driftavbrott.filter;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class CharResponseWrapper extends HttpServletResponseWrapper {
  private CharArrayWriter output;

  public String toString() {
    return output.toString();
  }

  public CharResponseWrapper(HttpServletResponse response) {
    super(response);
    output = new CharArrayWriter();
  }

  public PrintWriter getWriter() {
    return new PrintWriter(output);
  }
}
