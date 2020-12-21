/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.drill.exec.store.xml;

import org.apache.drill.categories.RowSetTests;
import org.apache.drill.common.types.TypeProtos.DataMode;
import org.apache.drill.common.types.TypeProtos.MinorType;
import org.apache.drill.exec.physical.rowSet.RowSet;
import org.apache.drill.exec.record.metadata.SchemaBuilder;
import org.apache.drill.exec.record.metadata.TupleMetadata;
import org.apache.drill.test.ClusterFixture;
import org.apache.drill.test.ClusterTest;
import org.apache.drill.test.rowSet.RowSetComparison;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.file.Paths;

import static org.apache.drill.test.QueryTestUtil.generateCompressedFile;
import static org.apache.drill.test.rowSet.RowSetUtilities.mapArray;
import static org.apache.drill.test.rowSet.RowSetUtilities.objArray;
import static org.apache.drill.test.rowSet.RowSetUtilities.strArray;
import static org.junit.Assert.assertEquals;

@Category(RowSetTests.class)
public class TestXMLReader extends ClusterTest {

  @BeforeClass
  public static void setup() throws Exception {
    ClusterTest.startCluster(ClusterFixture.builder(dirTestWatcher));

    XMLFormatConfig formatConfig = new XMLFormatConfig(null, 2);
    cluster.defineFormat("cp", "xml", formatConfig);
    cluster.defineFormat("dfs", "xml", formatConfig);

    // Needed for compressed file unit test
    dirTestWatcher.copyResourceToRoot(Paths.get("xml/"));
  }

  /**
   * This unit test tests a simple XML file with no nesting or attributes
   * @throws Exception Throw exception if anything goes wrong
   */
  @Test
  public void testWildcard() throws Exception {
    String sql = "SELECT * FROM cp.`xml/simple.xml`";
    RowSet results = client.queryBuilder().sql(sql).rowSet();
    assertEquals(3, results.rowCount());


    TupleMetadata expectedSchema = new SchemaBuilder()
      .add("attributes", MinorType.MAP)
      .addNullable("groupID", MinorType.VARCHAR)
      .addNullable("artifactID", MinorType.VARCHAR)
      .addNullable("version", MinorType.VARCHAR)
      .addNullable("classifier", MinorType.VARCHAR)
      .addNullable("scope", MinorType.VARCHAR)
      .buildSchema();

    RowSet expected = client.rowSetBuilder(expectedSchema)
      .addRow(mapArray(), "org.apache.drill.exec", "drill-java-exec", "${project.version}", null, null)
      .addRow(mapArray(),"org.apache.drill.exec", "drill-java-exec", "${project.version}", "tests", "test")
      .addRow(mapArray(),"org.apache.drill", "drill-common", "${project.version}", "tests", "test")
      .build();

    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  /**
   * This unit test tests a simple XML file with no nesting or attributes, but with explicitly selected fields.
   * @throws Exception Throw exception if anything goes wrong
   */
  @Test
  public void testExplicitWithSimpleXMLFile() throws Exception {
    String sql = "SELECT groupID, artifactID, version, classifier, scope FROM cp.`xml/simple.xml`";
    RowSet results = client.queryBuilder().sql(sql).rowSet();

    assertEquals(3, results.rowCount());

    TupleMetadata expectedSchema = new SchemaBuilder()
      .addNullable("groupID", MinorType.VARCHAR)
      .addNullable("artifactID", MinorType.VARCHAR)
      .addNullable("version", MinorType.VARCHAR)
      .addNullable("classifier", MinorType.VARCHAR)
      .addNullable("scope", MinorType.VARCHAR)
      .buildSchema();

    RowSet expected = client.rowSetBuilder(expectedSchema)
      .addRow("org.apache.drill.exec", "drill-java-exec", "${project.version}", null, null)
      .addRow("org.apache.drill.exec", "drill-java-exec", "${project.version}", "tests", "test")
      .addRow("org.apache.drill", "drill-common", "${project.version}", "tests", "test")
      .build();

    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  @Test
  public void testWildcardWithFilter() throws Exception {
    String sql = "SELECT * FROM cp.`xml/simple.xml` WHERE scope='test'";
    RowSet results = client.queryBuilder().sql(sql).rowSet();
    assertEquals(2, results.rowCount());


    TupleMetadata expectedSchema = new SchemaBuilder()
      .add("attributes", MinorType.MAP)
      .addNullable("groupID", MinorType.VARCHAR)
      .addNullable("artifactID", MinorType.VARCHAR)
      .addNullable("version", MinorType.VARCHAR)
      .addNullable("classifier", MinorType.VARCHAR)
      .addNullable("scope", MinorType.VARCHAR)
      .buildSchema();

    RowSet expected = client.rowSetBuilder(expectedSchema)
      .addRow(mapArray(),"org.apache.drill.exec", "drill-java-exec", "${project.version}", "tests", "test")
      .addRow(mapArray(),"org.apache.drill", "drill-common", "${project.version}", "tests", "test")
      .build();

    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  @Test
  public void testWildcardWithSingleNestedDataField() throws Exception {
    String sql = "SELECT * FROM cp.`xml/really-simple-nested.xml`";
    RowSet results = client.queryBuilder().sql(sql).rowSet();
    assertEquals(3, results.rowCount());

    TupleMetadata expectedSchema = new SchemaBuilder()
      .add("attributes", MinorType.MAP, DataMode.REQUIRED)
      .addMap("field1")
        .addNullable("key1", MinorType.VARCHAR)
        .addNullable("key2", MinorType.VARCHAR)
      .resumeSchema()
      .buildSchema();

    RowSet expected = client.rowSetBuilder(expectedSchema)
      .addRow(mapArray(), strArray("value1", "value2"))
      .addRow(mapArray(), strArray("value3", "value4"))
      .addRow(mapArray(), strArray("value5", "value6"))
      .build();

    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  @Test
  public void testExplicitWithSingleNestedDataField() throws Exception {
    String sql = "SELECT t1.field1.key1 as key1, t1.field1.key2 as key2 FROM cp.`xml/really-simple-nested.xml` as t1";
    RowSet results = client.queryBuilder().sql(sql).rowSet();
    assertEquals(3, results.rowCount());

    TupleMetadata expectedSchema = new SchemaBuilder()
      .addNullable("key1", MinorType.VARCHAR)
      .addNullable("key2", MinorType.VARCHAR)
      .buildSchema();

    RowSet expected = client.rowSetBuilder(expectedSchema)
      .addRow("value1", "value2")
      .addRow("value3", "value4")
      .addRow("value5", "value6")
      .build();

    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  @Test
  public void testSerDe() throws Exception {
    String sql = "SELECT COUNT(*) FROM cp.`xml/simple.xml`";
    String plan = queryBuilder().sql(sql).explainJson();
    long cnt = queryBuilder().physical(plan).singletonLong();
    assertEquals("Counts should match", 3L, cnt);
  }

  @Test
  public void testExplicitWithCompressedSimpleXMLFile() throws Exception {
    generateCompressedFile("xml/simple.xml", "zip", "xml/simple.xml.zip");

    String sql = "SELECT groupID, artifactID, version, classifier, scope FROM dfs.`xml/simple.xml.zip`";
    RowSet results = client.queryBuilder().sql(sql).rowSet();

    assertEquals(3, results.rowCount());

    TupleMetadata expectedSchema = new SchemaBuilder()
      .addNullable("groupID", MinorType.VARCHAR)
      .addNullable("artifactID", MinorType.VARCHAR)
      .addNullable("version", MinorType.VARCHAR)
      .addNullable("classifier", MinorType.VARCHAR)
      .addNullable("scope", MinorType.VARCHAR)
      .buildSchema();

    RowSet expected = client.rowSetBuilder(expectedSchema)
      .addRow("org.apache.drill.exec", "drill-java-exec", "${project.version}", null, null)
      .addRow("org.apache.drill.exec", "drill-java-exec", "${project.version}", "tests", "test")
      .addRow("org.apache.drill", "drill-common", "${project.version}", "tests", "test")
      .build();

    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  @Test
  public void testDeepNestedSpecificFields() throws Exception {
    String sql = "select xml.level2.level3.level4.level5.level6.level7.field1 as field1, xml.level2.level3.level4.level5.level6.level7.field2 as field2, xml.level2.level3.level4" +
      ".level5.level6.level7.field3 as field3 FROM cp.`xml/deep-nested.xml` as xml";
    RowSet results = client.queryBuilder().sql(sql).rowSet();

    assertEquals(2, results.rowCount());

    TupleMetadata expectedSchema = new SchemaBuilder()
      .addNullable("field1", MinorType.VARCHAR)
      .addNullable("field2", MinorType.VARCHAR)
      .addNullable("field3", MinorType.VARCHAR)
      .build();

    RowSet expected = client.rowSetBuilder(expectedSchema)
      .addRow("f1", "f2", "f3")
      .addRow("f4", "f5", "f6")
      .build();

    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  @Test
  public void testDeepNesting() throws Exception {
    String sql = "SELECT * FROM cp.`xml/deep-nested.xml`";
    RowSet results = client.queryBuilder().sql(sql).rowSet();

    assertEquals(2, results.rowCount());

    TupleMetadata expectedSchema = new SchemaBuilder()
      .add("attributes", MinorType.MAP, DataMode.REQUIRED)
      .addMap("level2")
        .addNullable("field1-level2", MinorType.VARCHAR)
        .addMap("level3")
        .addNullable("field1-level3", MinorType.VARCHAR)
          .addMap("level4")
          .addNullable("field1-level4", MinorType.VARCHAR)
            .addMap("level5")
            .addNullable("field1-level5", MinorType.VARCHAR)
              .addMap("level6")
              .addNullable("field1-level6", MinorType.VARCHAR)
                .addMap("level7")
                .addNullable("field1", MinorType.VARCHAR)
                .addNullable("field2", MinorType.VARCHAR)
                .addNullable("field3", MinorType.VARCHAR)
              .resumeMap()  // End level 7
              .resumeMap()   // End level 6
            .resumeMap() // End level 5
          .resumeMap() // End level 4
        .resumeMap() // End level 3
      .resumeSchema()
      .build();

    RowSet expected = client.rowSetBuilder(expectedSchema)
      .addRow(mapArray(), objArray(
        objArray(
          "l2",
          objArray("l3",
            objArray("l4",
              objArray("l5",
                objArray("l6",
                  strArray("f1", "f2", "f3")
                )
              )
            )
          )
        )
      ))
      .addRow(mapArray(), objArray(
        objArray(
          null,
          objArray(null,
            objArray(null,
              objArray(null,
                objArray(null,
                  strArray("f4", "f5", "f6")
                )
              )
            )
          )
        )
      ))
      .build();

    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  @Test
  public void testDataLevel() throws Exception {
    String sql = "SELECT * FROM table(cp.`xml/deep-nested2.xml` (type => 'xml', dataLevel => 8))";
    RowSet results = client.queryBuilder().sql(sql).rowSet();

    TupleMetadata expectedSchema = new SchemaBuilder()
      .add("attributes", MinorType.MAP, DataMode.REQUIRED)
      .addNullable("field1", MinorType.VARCHAR)
      .addNullable("field2", MinorType.VARCHAR)
      .addNullable("field3", MinorType.VARCHAR)
      .addNullable("field1-level6", MinorType.VARCHAR)
      .build();

    RowSet expected = client.rowSetBuilder(expectedSchema)
      .addRow(mapArray(), "f4", "f5", "f6", null)
      .addRow(mapArray(), "f1", "f2", "f3", "l6")
      .build();

    assertEquals(2, results.rowCount());
    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  @Test
  public void testExplicitDataLevel() throws Exception {
    String sql = "SELECT field1, field2, field3 FROM table(cp.`xml/deep-nested2.xml` (type => 'xml', dataLevel => 8))";
    RowSet results = client.queryBuilder().sql(sql).rowSet();

    TupleMetadata expectedSchema = new SchemaBuilder()
      .addNullable("field1", MinorType.VARCHAR)
      .addNullable("field2", MinorType.VARCHAR)
      .addNullable("field3", MinorType.VARCHAR)
      .build();

    RowSet expected = client.rowSetBuilder(expectedSchema)
      .addRow("f4", "f5", "f6")
      .addRow("f1", "f2", "f3")
      .build();

    assertEquals(2, results.rowCount());
    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  @Test
  public void testComplexWildcardStar() throws Exception {
    String sql = "SELECT * FROM cp.`xml/nested.xml`";
    RowSet results = client.queryBuilder().sql(sql).rowSet();

    TupleMetadata expectedSchema = new SchemaBuilder()
      .add("attributes", MinorType.MAP, DataMode.REQUIRED)
      .addMap("field1")
        .addNullable("key1", MinorType.VARCHAR)
        .addNullable("key2", MinorType.VARCHAR)
      .resumeSchema()
      .addMap("field2")
        .addNullable("key3", MinorType.VARCHAR)
        .addMap("nestedField1")
          .addNullable("nk1", MinorType.VARCHAR)
          .addNullable("nk2", MinorType.VARCHAR)
          .addNullable("nk3", MinorType.VARCHAR)
        .resumeMap()
      .resumeSchema()
      .buildSchema();

    RowSet expected = client.rowSetBuilder(expectedSchema)
      .addRow(mapArray(), strArray("value1", "value2"), objArray("k1", strArray("nk_value1", "nk_value2", "nk_value3")))
      .addRow(mapArray(), strArray("value3", "value4"), objArray("k2", strArray("nk_value4", "nk_value5", "nk_value6")))
      .addRow(mapArray(), strArray("value5", "value6"), objArray("k3", strArray("nk_value7", "nk_value8", "nk_value9")))
      .build();

    assertEquals(3, results.rowCount());
    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  @Test
  public void testComplexNestedExplicit() throws Exception {
    String sql = "SELECT xml.field2.nestedField1.nk1 as nk1, xml.field2.nestedField1.nk2 as nk2, xml.field2.nestedField1.nk3 as nk3 FROM cp.`xml/nested.xml` AS xml";
    RowSet results = client.queryBuilder().sql(sql).rowSet();

    TupleMetadata expectedSchema = new SchemaBuilder()
      .addNullable("nk1", MinorType.VARCHAR)
      .addNullable("nk2", MinorType.VARCHAR)
      .addNullable("nk3", MinorType.VARCHAR)
      .build();

    RowSet expected = client.rowSetBuilder(expectedSchema)
      .addRow("nk_value1", "nk_value2", "nk_value3")
      .addRow("nk_value4", "nk_value5", "nk_value6")
      .addRow("nk_value7", "nk_value8", "nk_value9")
      .build();

    assertEquals(3, results.rowCount());
    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  @Test
  public void testAttributes() throws Exception {
    String sql = "SELECT attributes FROM cp.`xml/attributes.xml`";
    RowSet results = client.queryBuilder().sql(sql).rowSet();

    TupleMetadata expectedSchema = new SchemaBuilder()
      .addMap("attributes")
        .addNullable("title_binding", MinorType.VARCHAR)
        .addNullable("title_subcategory", MinorType.VARCHAR)
      .resumeSchema()
      .build();

    RowSet expected = client.rowSetBuilder(expectedSchema)
      .addRow((Object) mapArray(null, null))
      .addRow((Object) strArray("paperback", null))
      .addRow((Object) strArray("hardcover", "non-fiction"))
      .build();

    assertEquals(3, results.rowCount());
    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  @Test
  public void testLimitPushdown() throws Exception {
    String sql = "SELECT * FROM cp.`xml/simple.xml` LIMIT 2";

    queryBuilder()
      .sql(sql)
      .planMatcher()
      .include("Limit", "maxRecords=2")
      .match();
  }
}