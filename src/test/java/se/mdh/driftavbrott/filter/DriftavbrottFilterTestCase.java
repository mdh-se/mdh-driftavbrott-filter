package se.mdh.driftavbrott.filter;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DriftavbrottFilterTestCase {
  @Test
  public void isExcluded() {
    List<String> excludes = new ArrayList<>();
    excludes.add("/path/to");
    assertFalse(DriftavbrottFilter.isExcluded(excludes, null));
    assertFalse(DriftavbrottFilter.isExcluded(excludes, ""));
    assertFalse(DriftavbrottFilter.isExcluded(excludes, "/"));
    assertFalse(DriftavbrottFilter.isExcluded(excludes, "/path"));
    assertTrue(DriftavbrottFilter.isExcluded(excludes, "/path/to"));
    assertTrue(DriftavbrottFilter.isExcluded(excludes, "/path/to/folder"));
  }

  @Test
  public void parseExcludesNull() {
    List<String> excludes = DriftavbrottFilter.parseExcludes(null);
    assertNotNull(excludes);
    assertTrue(excludes.isEmpty());
  }
  @Test
  public void parseExcludesTom() {
    List<String> excludes = DriftavbrottFilter.parseExcludes("");
    assertNotNull(excludes);
    assertTrue(excludes.isEmpty());
  }
  @Test
  public void parseExcludes1() {
    String ONE = "/ett";
    List<String> excludes = DriftavbrottFilter.parseExcludes(ONE);
    assertNotNull(excludes);
    assertEquals(1, excludes.size());
    assertEquals(ONE, excludes.get(0));
  }

  @Test
  public void parseExcludes2() {
    List<String> excludes = DriftavbrottFilter.parseExcludes("/ett /två");
    assertNotNull(excludes);
    assertEquals(2, excludes.size());
    assertEquals("/ett", excludes.get(0));
    assertEquals("/två", excludes.get(1));
  }
}