package systems.riddler.uxid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.module.ModuleDescriptor;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ModuleDescriptorTest {

  @Test // (sabotage: module renamed systems.riddler.uxidx -> the name assertion failed)
  void theModuleIsNamedForThePackage() {
    assertEquals("systems.riddler.uxid", descriptor().name());
  }

  @Test // (sabotage: the exports line removed -> the exports assertion failed)
  void theModuleExportsItsOnePackageToEveryReader() {
    Set<String> exports =
        descriptor().exports().stream()
            .filter(export -> !export.isQualified())
            .map(ModuleDescriptor.Exports::source)
            .collect(Collectors.toSet());
    assertEquals(Set.of("systems.riddler.uxid"), exports);
  }

  @Test // (sabotage: a requires java.logging added -> the requires assertion failed)
  void theModuleRequiresNothingButJavaBase() {
    Set<String> requires =
        descriptor().requires().stream()
            .map(ModuleDescriptor.Requires::name)
            .collect(Collectors.toSet());
    assertEquals(Set.of("java.base"), requires);
  }

  private static ModuleDescriptor descriptor() {
    Module module = ModuleDescriptorTest.class.getModule();
    assertTrue(module.isNamed(), "the tests run inside the library's module");
    return module.getDescriptor();
  }
}
