import java.net.URL
import java.net.URLClassLoader

class ChildFirstClassLoader(classpath: Array<URL>?, parent: ClassLoader?) : URLClassLoader(classpath, parent) {
    private val system: ClassLoader = getSystemClassLoader()

    @Synchronized
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        var c = findLoadedClass(name)
        if (c == null) {
            c = try {
                findClass(name)
            } catch (e: ClassNotFoundException) {
                try {
                    super.loadClass(name, resolve)
                } catch (e2: ClassNotFoundException) {
                    system.loadClass(name)
                }
            }
        }
        if (resolve) resolveClass(c)
        return c
    }
}