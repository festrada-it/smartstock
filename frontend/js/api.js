// Sesión del usuario (token JWT guardado en localStorage) y helpers para llamar a los microservicios.

const CLAVE_SESION = 'smartstock_sesion';

const Sesion = {
    guardar(datos) {
        localStorage.setItem(CLAVE_SESION, JSON.stringify(datos));
    },
    obtener() {
        return JSON.parse(localStorage.getItem(CLAVE_SESION) || 'null');
    },
    cerrar() {
        localStorage.removeItem(CLAVE_SESION);
        window.location.href = 'login.html';
    },
    esAdmin() {
        return this.obtener()?.rol === 'ADMIN';
    },
    /** Redirige al login si no hay sesión, o a su pantalla de inicio si el rol no tiene acceso. */
    requerir(rolesPermitidos) {
        const sesion = this.obtener();
        if (!sesion) {
            window.location.href = 'login.html';
            return null;
        }
        if (rolesPermitidos && !rolesPermitidos.includes(sesion.rol)) {
            window.location.href = paginaInicio(sesion.rol);
            return null;
        }
        return sesion;
    },
};

function paginaInicio(rol) {
    return rol === 'ADMIN' ? 'dashboard.html' : 'ventas.html';
}

const MENSAJES_HTTP = {
    403: 'No tenés permiso para realizar esta acción',
    404: 'No se encontró el recurso pedido',
};

async function llamarApi(baseUrl, ruta, opciones = {}) {
    const sesion = Sesion.obtener();
    const headers = { 'Content-Type': 'application/json' };
    if (sesion) headers.Authorization = `Bearer ${sesion.token}`;

    let respuesta;
    try {
        respuesta = await fetch(baseUrl + ruta, {
            method: opciones.method || 'GET',
            headers,
            body: opciones.body !== undefined ? JSON.stringify(opciones.body) : undefined,
        });
    } catch {
        throw new Error('No se pudo conectar con el servidor. Revisá que los servicios estén levantados.');
    }

    if (respuesta.status === 401 && sesion) {
        Sesion.cerrar();
        throw new Error('La sesión expiró');
    }
    if (!respuesta.ok) {
        // Mensaje por defecto si el servicio no manda cuerpo JSON (por ejemplo, el 403 de Spring Security).
        let mensaje = MENSAJES_HTTP[respuesta.status] || `Error ${respuesta.status}`;
        let datos = null;
        try {
            datos = await respuesta.json();
            mensaje = datos.error || datos.detail || mensaje;
        } catch { /* respuesta sin cuerpo JSON */ }
        // status y datos permiten distinguir rechazos, por ejemplo el de stock insuficiente (#72).
        throw Object.assign(new Error(mensaje), { status: respuesta.status, datos });
    }
    return respuesta.status === 204 ? null : respuesta.json();
}

const coreApi = (ruta, opciones) => llamarApi(CONFIG.CORE_API, ruta, opciones);
const analyticsApi = (ruta, opciones) => llamarApi(CONFIG.ANALYTICS_API, ruta, opciones);

function escaparHtml(texto) {
    const div = document.createElement('div');
    div.textContent = texto ?? '';
    return div.innerHTML;
}

function formatoMoneda(valor) {
    return new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' }).format(valor ?? 0);
}

function formatoFecha(isoFecha) {
    if (!isoFecha) return '-';
    const [anio, mes, dia] = isoFecha.slice(0, 10).split('-');
    return `${dia}/${mes}/${anio}`;
}

/** Muestra un mensaje temporal (tipo: success | danger | warning | info) en el contenedor #mensajes. */
function mostrarMensaje(texto, tipo = 'success') {
    const contenedor = document.getElementById('mensajes');
    if (!contenedor) return alert(texto);
    const alerta = document.createElement('div');
    alerta.className = `alert alert-${tipo} alert-dismissible fade show`;
    alerta.innerHTML = `${escaparHtml(texto)}<button type="button" class="btn-close" data-bs-dismiss="alert"></button>`;
    contenedor.prepend(alerta);
    setTimeout(() => alerta.remove(), 6000);
}
