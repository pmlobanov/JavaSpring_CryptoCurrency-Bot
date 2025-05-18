import org.junit.Test;
import org.springframework.modulith.core.ApplicationModules;
import spbstu.mcs.telegramBot.Application;

public class ModulithTest {

    @Test
    public void verifiesModularStructure() {
        ApplicationModules modules = ApplicationModules.of(Application.class);
        modules.verify();
    }
}
