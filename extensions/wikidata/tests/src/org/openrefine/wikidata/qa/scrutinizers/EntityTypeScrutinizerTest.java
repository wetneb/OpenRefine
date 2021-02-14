package org.openrefine.wikidata.qa.scrutinizers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.openrefine.wikidata.qa.ConstraintFetcher;
import org.openrefine.wikidata.qa.MockConstraintFetcher;
import org.openrefine.wikidata.testing.TestingData;
import org.testng.annotations.Test;
import org.wikidata.wdtk.datamodel.helpers.Datamodel;
import org.wikidata.wdtk.datamodel.interfaces.ItemIdValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Snak;
import org.wikidata.wdtk.datamodel.interfaces.SnakGroup;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.Value;
import org.wikidata.wdtk.datamodel.interfaces.ValueSnak;

public class EntityTypeScrutinizerTest extends StatementScrutinizerTest {
    
    private static ItemIdValue qid = Datamodel.makeWikidataItemIdValue("Q343");

    @Override
    public EditScrutinizer getScrutinizer() {
        return new EntityTypeScrutinizer();
    }
    
    @Test
    public void testAllowed() {
        scrutinize(TestingData.generateStatement(qid, qid));
        assertNoWarningRaised();
    }

    @Test
    public void testDisallowed() {
        scrutinize(TestingData.generateStatement(qid, MockConstraintFetcher.propertyOnlyPid, qid));
        assertWarningsRaised(EntityTypeScrutinizer.type);
    }
}
