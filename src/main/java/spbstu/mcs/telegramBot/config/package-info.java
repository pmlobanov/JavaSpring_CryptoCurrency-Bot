@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "server",
                "cryptoApi",
                "service",
                "DB::services",
                "model",
                "controller",
                "security"
        }
)
package spbstu.mcs.telegramBot.config;