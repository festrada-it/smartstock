// Tailwind con prefijo "tw-" y sin preflight para que conviva con Bootstrap.
// Bootstrap aporta componentes (modales, formularios, tablas); Tailwind el layout y los detalles visuales.
tailwind.config = {
    prefix: 'tw-',
    corePlugins: { preflight: false },
    theme: {
        extend: {
            colors: {
                marca: { DEFAULT: '#5b2d90', oscuro: '#45206f', claro: '#efe7f8' },
            },
        },
    },
};
