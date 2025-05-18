@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "DB::services",
                "model"
        }
)
package spbstu.mcs.telegramBot.cryptoApi;