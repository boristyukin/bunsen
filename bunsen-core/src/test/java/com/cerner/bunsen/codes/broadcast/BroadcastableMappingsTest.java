package com.cerner.bunsen.codes.broadcast;

import com.google.common.collect.ImmutableList;
import java.util.Collections;
import org.apache.spark.broadcast.Broadcast;
import org.apache.spark.sql.SparkSession;
import org.hl7.fhir.dstu3.model.ConceptMap;
import org.hl7.fhir.dstu3.model.ConceptMap.ConceptMapGroupComponent;
import org.hl7.fhir.dstu3.model.ConceptMap.ConceptMapGroupUnmappedComponent;
import org.hl7.fhir.dstu3.model.ConceptMap.ConceptMapGroupUnmappedMode;
import org.hl7.fhir.dstu3.model.UriType;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test of broadcast mappings.
 */
public class BroadcastableMappingsTest {

  private static SparkSession spark;

  private static Broadcast<BroadcastableMappings> broadcast;

  /**
   * Sets up Spark and concept maps for testing.
   */
  @BeforeClass
  public static void setUp() {
    spark = SparkSession.builder()
        .master("local[2]")
        .appName("BroadcastableMappingsTest")
        .getOrCreate();

    ConceptMap conceptMap = new ConceptMap();

    conceptMap.setUrl("uri:test:concept:map")
        .setVersion("0")
        .setSource(new UriType("uri:test:source:valueset"))
        .setTarget(new UriType("uri:test:target:valueset"));

    ConceptMapGroupComponent group = conceptMap.addGroup()
        .setSource("uri:test:source:system")
        .setTarget("uri:test:target:system");

    group.addElement().setCode("abc").addTarget().setCode("123");
    group.addElement().setCode("def").addTarget().setCode("456");

    ConceptMap delegatingMap = new ConceptMap();

    delegatingMap.setUrl("uri:test:concept:delegating")
        .setVersion("0")
        .setSource(new UriType("uri:test:source:valueset"))
        .setTarget(new UriType("uri:test:target:valueset"));

    delegatingMap.addGroup()
        .setSource("uri:test:source:system")
        .setTarget("uri:test:target:system")
        .setUnmapped(new ConceptMapGroupUnmappedComponent()
            .setMode(ConceptMapGroupUnmappedMode.OTHERMAP)
            .setUrl("uri:test:concept:map"));

    broadcast = BroadcastableMappings.broadcast(spark,
        ImmutableList.of(conceptMap, delegatingMap));
  }

  /**
   * Tears down Spark.
   */
  @AfterClass
  public static void tearDown() {
    spark.stop();
    spark = null;
  }

  @Test
  public void testFoundMapping() {

    BroadcastableConceptMap broadcastableConceptMap =
        broadcast.getValue()
            .getBroadcastConceptMap("uri:test:concept:map");

    BroadcastableConceptMap.CodeValue value = broadcastableConceptMap
        .getTarget("uri:test:source:system", "abc").get(0);

    Assert.assertEquals("uri:test:target:system", value.getSystem());
    Assert.assertEquals("123", value.getValue());
  }

  @Test
  public void testDelegateMapping() {

    BroadcastableConceptMap broadcastableConceptMap =
        broadcast.getValue()
            .getBroadcastConceptMap("uri:test:concept:delegating");

    BroadcastableConceptMap.CodeValue value = broadcastableConceptMap
        .getTarget("uri:test:source:system", "abc").get(0);

    Assert.assertEquals("uri:test:target:system", value.getSystem());
    Assert.assertEquals("123", value.getValue());
  }

  @Test
  public void testNoValue() {

    Assert.assertEquals(broadcast.getValue()
            .getBroadcastConceptMap("uri:test:concept:map")
            .getTarget("uri:test:source:system", "nosuchvalue"),
        Collections.emptyList());
  }

  @Test
  public void testNoSystem() {

    Assert.assertEquals(broadcast.getValue()
            .getBroadcastConceptMap("uri:test:concept:map")
            .getTarget("uri:test:source:nosuchsystem", "abc"),
        Collections.emptyList());
  }
}
