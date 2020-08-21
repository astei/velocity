package com.velocitypowered.proxy.plugin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.meta.PluginDependency;
import com.velocitypowered.proxy.plugin.loader.VelocityPluginDescription;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class PluginDependencyUtilsTest {

  private static final PluginDescription NO_DEPENDENCY = testDescription("trivial");
  private static final PluginDescription NO_DEPENDENCY_2 = testDescription("trivial2");
  private static final PluginDescription HAS_DEPENDENCY_1 = testDescription("dependent1",
      new PluginDependency("trivial", null, false));
  private static final PluginDescription HAS_DEPENDENCY_2 = testDescription("dependent2",
      new PluginDependency("dependent1", null, false));
  private static final PluginDescription HAS_DEPENDENCY_3 = testDescription("dependent3",
      new PluginDependency("trivial", null, false));

  private static final PluginDescription CIRCULAR_DEPENDENCY_1 = testDescription("circle",
      new PluginDependency("oval", "", false));
  private static final PluginDescription CIRCULAR_DEPENDENCY_2 = testDescription("oval",
      new PluginDependency("circle", "", false));

  @Test
  void sortCandidatesTrivial() throws Exception {
    List<PluginDescription> descriptionList = new ArrayList<>();
    assertEquals(descriptionList, PluginDependencyUtils.sortCandidates(descriptionList));
  }

  @Test
  void sortCandidatesSingleton() throws Exception {
    List<PluginDescription> plugins = ImmutableList.of(NO_DEPENDENCY);
    assertEquals(plugins, PluginDependencyUtils.sortCandidates(plugins));
  }

  @Test
  void sortCandidatesBasicDependency() throws Exception {
    List<PluginDescription> plugins = ImmutableList.of(HAS_DEPENDENCY_1, NO_DEPENDENCY);
    List<PluginDescription> expected = ImmutableList.of(NO_DEPENDENCY, HAS_DEPENDENCY_1);
    assertEquals(expected, PluginDependencyUtils.sortCandidates(plugins));
  }

  @Test
  void sortCandidatesNestedDependency() throws Exception {
    List<PluginDescription> plugins = ImmutableList.of(HAS_DEPENDENCY_1, HAS_DEPENDENCY_2,
        NO_DEPENDENCY);
    List<PluginDescription> expected = ImmutableList.of(NO_DEPENDENCY, HAS_DEPENDENCY_1,
        HAS_DEPENDENCY_2);
    assertEquals(expected, PluginDependencyUtils.sortCandidates(plugins));
  }

  @Test
  void sortCandidatesTypical() throws Exception {
    List<PluginDescription> plugins = ImmutableList.of(HAS_DEPENDENCY_2, NO_DEPENDENCY_2,
        HAS_DEPENDENCY_1, NO_DEPENDENCY);
    List<PluginDescription> expected = ImmutableList.of(NO_DEPENDENCY, HAS_DEPENDENCY_1,
        HAS_DEPENDENCY_2, NO_DEPENDENCY_2);
    assertEquals(expected, PluginDependencyUtils.sortCandidates(plugins));
  }

  @Test
  void sortCandidatesMultiplePluginsDependentOnOne() throws Exception {
    List<PluginDescription> plugins = ImmutableList.of(HAS_DEPENDENCY_3, HAS_DEPENDENCY_1,
        NO_DEPENDENCY);
    List<PluginDescription> expected = ImmutableList.of(NO_DEPENDENCY, HAS_DEPENDENCY_1,
        HAS_DEPENDENCY_3);
    assertEquals(expected, PluginDependencyUtils.sortCandidates(plugins));
  }

  @Test
  void sortCandidatesCircularDependency() throws Exception {
    List<PluginDescription> descs = ImmutableList.of(CIRCULAR_DEPENDENCY_1, CIRCULAR_DEPENDENCY_2);
    assertThrows(IllegalStateException.class, () -> PluginDependencyUtils.sortCandidates(descs));
  }

  private static PluginDescription testDescription(String id, PluginDependency... dependencies) {
    return new VelocityPluginDescription(
        id, "tuxed", "0.1", null, null, ImmutableList.of(),
        ImmutableList.copyOf(dependencies), null, null
    );
  }
}
