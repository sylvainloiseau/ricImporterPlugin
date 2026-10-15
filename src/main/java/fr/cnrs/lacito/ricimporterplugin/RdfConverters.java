package fr.cnrs.lacito.ricimporterplugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Looks up {@link RdfConverterProvider}s registered with {@link ServiceLoader}.
 *
 * <p>Lookups are not cached: each call scans the class loader again, so providers added to a
 * plugin class loader at runtime are seen.
 */
public final class RdfConverters {

    private RdfConverters() {
    }

    /** Providers visible from the thread context class loader, sorted by name. */
    public static List<RdfConverterProvider> available() {
        return available(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Providers visible from {@code classLoader}, sorted by name.
     *
     * @throws IllegalStateException if two providers share a name
     */
    public static List<RdfConverterProvider> available(ClassLoader classLoader) {
        Map<String, RdfConverterProvider> byName = new LinkedHashMap<>();
        for (RdfConverterProvider provider : ServiceLoader.load(RdfConverterProvider.class, classLoader)) {
            RdfConverterProvider previous = byName.putIfAbsent(provider.name(), provider);
            if (previous != null) {
                throw new IllegalStateException("Two converter providers are named '" + provider.name() + "': "
                        + previous.getClass().getName() + " and " + provider.getClass().getName());
            }
        }
        List<RdfConverterProvider> providers = new ArrayList<>(byName.values());
        providers.sort(Comparator.comparing(RdfConverterProvider::name));
        return providers;
    }

    public static Optional<RdfConverterProvider> find(String name) {
        return find(name, Thread.currentThread().getContextClassLoader());
    }

    public static Optional<RdfConverterProvider> find(String name, ClassLoader classLoader) {
        return available(classLoader).stream().filter(p -> p.name().equals(name)).findFirst();
    }

    /**
     * @throws IllegalArgumentException if no provider has this name; the message lists the
     *                                  available names
     */
    public static RdfConverterProvider get(String name) {
        return get(name, Thread.currentThread().getContextClassLoader());
    }

    public static RdfConverterProvider get(String name, ClassLoader classLoader) {
        List<RdfConverterProvider> providers = available(classLoader);
        return providers.stream()
                .filter(p -> p.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No converter named '" + name + "'. Available: "
                        + providers.stream().map(RdfConverterProvider::name).collect(Collectors.joining(", ", "[", "]"))));
    }
}
