# kotlinx.serialization and Retrofit ship their own consumer rules.

# SlowCallInterceptor reads this annotation from the Retrofit method at run time.
-keep @interface build.conductor.android.client.data.api.SlowCall
