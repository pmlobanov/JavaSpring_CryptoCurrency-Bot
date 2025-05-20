@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "server",
                "cryptoApi",
                "service",
                "DB::services",
                "model",
                "security"
        }
)
package spbstu.mcs.telegramBot.config;