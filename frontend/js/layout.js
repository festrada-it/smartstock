// Menú lateral común a todas las pantallas. Muestra solo las opciones permitidas para el rol.

const MENU = [
    { href: 'dashboard.html', texto: 'Monitor predictivo', icono: '📈', roles: ['ADMIN'] },
    { href: 'productos.html', texto: 'Productos y stock', icono: '📦', roles: ['ADMIN', 'VENDEDOR'] },
    { href: 'proveedores.html', texto: 'Proveedores', icono: '🚚', roles: ['ADMIN', 'VENDEDOR'] },
    { href: 'ventas.html', texto: 'Ventas', icono: '🛒', roles: ['ADMIN', 'VENDEDOR'] },
    { href: 'usuarios.html', texto: 'Usuarios y parámetros', icono: '⚙️', roles: ['ADMIN'] },
];

function renderLayout(sesion) {
    const paginaActual = window.location.pathname.split('/').pop() || 'index.html';
    const enlaces = MENU
        .filter(item => item.roles.includes(sesion.rol))
        .map(item => {
            const activo = item.href === paginaActual;
            return `
                <a href="${item.href}"
                   class="tw-flex tw-items-center tw-gap-3 tw-rounded-lg tw-px-3 tw-py-2 tw-no-underline
                          ${activo ? 'tw-bg-marca-claro tw-text-marca tw-font-semibold' : 'tw-text-slate-600 hover:tw-bg-slate-100'}">
                    <span>${item.icono}</span><span>${item.texto}</span>
                </a>`;
        })
        .join('');

    document.getElementById('sidebar').innerHTML = `
        <div class="tw-flex tw-h-full tw-flex-col tw-p-4">
            <div class="tw-mb-8 tw-flex tw-items-center tw-gap-2 tw-px-2">
                <div class="tw-flex tw-h-9 tw-w-9 tw-items-center tw-justify-center tw-rounded-lg tw-bg-marca tw-font-bold tw-text-white">S</div>
                <span class="tw-text-lg tw-font-bold tw-text-slate-800">SmartStock</span>
            </div>
            <nav class="tw-flex tw-flex-col tw-gap-1">${enlaces}</nav>
            <div class="tw-mt-auto tw-border-t tw-pt-4 tw-px-2">
                <div class="tw-text-sm tw-font-semibold tw-text-slate-800">${escaparHtml(sesion.nombre)}</div>
                <div class="tw-mb-3 tw-text-xs tw-text-slate-500">${sesion.rol === 'ADMIN' ? 'Administrador' : 'Vendedor'}</div>
                <button id="btn-salir" class="btn btn-sm btn-outline-secondary tw-w-full">Cerrar sesión</button>
            </div>
        </div>`;
    document.getElementById('btn-salir').addEventListener('click', () => Sesion.cerrar());
}
