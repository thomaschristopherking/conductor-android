package build.conductor.android.client

/** Reads a scrubbed live API response from src/test/resources/fixtures. */
object Fixtures {
    fun read(name: String): String =
        requireNotNull(Fixtures::class.java.classLoader?.getResource("fixtures/$name")) { "Missing fixture $name" }.readText()
}
