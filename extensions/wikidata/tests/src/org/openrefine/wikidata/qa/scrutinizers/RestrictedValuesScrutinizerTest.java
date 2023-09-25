package org.openrefine.wikidata.qa.scrutinizers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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

public class RestrictedValuesScrutinizerTest extends SnakScrutinizerTest {
    
    private ItemIdValue qid = Datamodel.makeWikidataItemIdValue("Q3487");

    @Override
    public EditScrutinizer getScrutinizer() {
        return new RestrictedValuesScrutinizer();
    }
    
    @Test
    public void testNoConstraint() {
        scrutinize(TestingData.generateStatement(qid,
                Datamodel.makeWikidataPropertyIdValue("P28732"),
                qid));
        assertNoWarningRaised();
    }
    
    @Test
    public void testAllowedValue() {
        scrutinize(TestingData.generateStatement(qid,
                MockConstraintFetcher.allowedValuesPid,
                MockConstraintFetcher.allowedValueQid));
        assertNoWarningRaised();
    }
    
    @Test
    public void testAllowedValueFailing() {
        scrutinize(TestingData.generateStatement(qid,
                MockConstraintFetcher.allowedValuesPid,
                qid));
        assertWarningsRaised(RestrictedValuesScrutinizer.type);
    }
    
    @Test
    public void testDisallowedValue() {
        scrutinize(TestingData.generateStatement(qid,
                MockConstraintFetcher.forbiddenValuesPid,
                qid));
        assertNoWarningRaised();
    }
    
    @Test
    public void testDisallowedValueFailing() {
        scrutinize(TestingData.generateStatement(qid,
                MockConstraintFetcher.forbiddenValuesPid,
                MockConstraintFetcher.forbiddenValueQid));
        assertWarningsRaised(RestrictedValuesScrutinizer.type);
    }

}
