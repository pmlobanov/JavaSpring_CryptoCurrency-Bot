@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "DB::services",
                "config",
                "security",
                "model",
                "controller",
                "cryptoApi",
                "service"
        }
)
package spbstu.mcs.telegramBot.server;