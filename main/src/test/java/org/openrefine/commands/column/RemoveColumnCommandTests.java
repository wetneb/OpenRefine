package org.openrefine.commands.column;

import java.io.IOException;

import javax.servlet.ServletException;

import org.openrefine.commands.CommandTestBase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RemoveColumnCommandTests extends CommandTestBase {
	
	@BeforeMethod
	public void setUpCommand() {
		command = new RemoveColumnCommand();
	}
	
	@Test
	public void testCSRFProtection() throws ServletException, IOException {
		command.doPost(request, response);
		assertCSRFCheckFailed();
	}
}
