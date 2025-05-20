@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "DB::services",
                "config",
                "security",
                "model",
                "cryptoApi",
                "service"
        }
)
package spbstu.mcs.telegramBot.server;