package org.openrefine.wikidata.qa.scrutinizers;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openrefine.wikidata.qa.ConstraintFetcher;
import org.openrefine.wikidata.qa.MockConstraintFetcher;
import org.openrefine.wikidata.testing.TestingData;
import org.openrefine.wikidata.updates.ItemUpdate;
import org.openrefine.wikidata.updates.ItemUpdateBuilder;
import org.testng.annotations.Test;
import org.wikidata.wdtk.datamodel.helpers.Datamodel;
import org.wikidata.wdtk.datamodel.interfaces.QuantityValue;
import org.wikidata.wdtk.datamodel.interfaces.Snak;
import org.wikidata.wdtk.datamodel.interfaces.SnakGroup;
import org.wikidata.wdtk.datamodel.interfaces.Statement;
import org.wikidata.wdtk.datamodel.interfaces.Value;

public class QuantityScrutinizerTest extends ValueScrutinizerTest{
    
    private QuantityValue exactValue = Datamodel.makeQuantityValue(
            new BigDecimal("1.234"));
    
    private QuantityValue integerValue = Datamodel.makeQuantityValue(
            new BigDecimal("132"));
    
    private QuantityValue trailingZeros = Datamodel.makeQuantityValue(
            new BigDecimal("132.00"));
    
    private QuantityValue valueWithBounds = Datamodel.makeQuantityValue(
            new BigDecimal("1.234"),
            new BigDecimal("1.200"),
            new BigDecimal("1.545"));
    
    private QuantityValue wrongUnitValue = Datamodel.makeQuantityValue(
            new BigDecimal("1.234"), "Q346721");
    
    private QuantityValue goodUnitValue = Datamodel.makeQuantityValue(
            new BigDecimal("1.234"), MockConstraintFetcher.allowedUnit.getIri());

    @Override
    public EditScrutinizer getScrutinizer() {
        return new QuantityScrutinizer();
    }
    
    @Test
    public void testBoundsAllowed() {
        scrutinize(valueWithBounds);
        assertNoWarningRaised();
    }
    
    @Test
    public void testBoundsDisallowed() {
        scrutinize(MockConstraintFetcher.noBoundsPid, valueWithBounds);
        assertWarningsRaised(QuantityScrutinizer.boundsDisallowedType);
    }

    @Test
    public void testFractionalAllowed() {
        scrutinize(exactValue);
        assertNoWarningRaised();
    }

    @Test
    public void testFractionalDisallowed() {
        scrutinize(MockConstraintFetcher.integerPid, exactValue);
        assertWarningsRaised(QuantityScrutinizer.integerConstraintType);
    }
    
    @Test
    public void testTrailingZeros() {
        scrutinize(MockConstraintFetcher.integerPid, trailingZeros);
        assertWarningsRaised(QuantityScrutinizer.integerConstraintType);
    }
    
    @Test
    public void testInteger() {
        scrutinize(MockConstraintFetcher.integerPid, integerValue);
        assertNoWarningRaised();
    }
    
    @Test
    public void testUnitReqired() {
        scrutinize(MockConstraintFetcher.allowedUnitsPid, integerValue);
        assertWarningsRaised(QuantityScrutinizer.noUnitProvidedType);
    }
    
    @Test
    public void testWrongUnit() {
        scrutinize(MockConstraintFetcher.allowedUnitsPid, wrongUnitValue);
        assertWarningsRaised(QuantityScrutinizer.invalidUnitType);
    }
    
    @Test
    public void testGoodUnit() {
        scrutinize(MockConstraintFetcher.allowedUnitsPid, goodUnitValue);
        assertNoWarningRaised();
    }
    
    @Test
    public void testUnitForbidden() {
        scrutinize(MockConstraintFetcher.noUnitsPid, goodUnitValue);
        assertWarningsRaised(QuantityScrutinizer.invalidUnitType);
    }
    
    @Test
    public void testNoUnit() {
        scrutinize(MockConstraintFetcher.noUnitsPid, integerValue);
        assertNoWarningRaised();
    }
}
