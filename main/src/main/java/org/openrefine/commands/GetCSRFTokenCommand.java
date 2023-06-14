
package org.openrefine.commands;

import java.io.IOException;
import java.util.Collections;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Generates a fresh CSRF token.
 */
public class GetCSRFTokenCommand extends Command {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        respondJSON(response, 200, Collections.singletonMap("token", csrfFactory.getFreshToken()));
    }
}
