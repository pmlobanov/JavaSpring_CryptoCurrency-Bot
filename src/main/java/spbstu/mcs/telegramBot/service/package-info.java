@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "DB::services",
                "model",
                "cryptoApi",
                "util"
        }
)
package spbstu.mcs.telegramBot.service;