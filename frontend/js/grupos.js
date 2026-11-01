const sesion = Sesion.requerir(['ADMIN']);

// API de core-service, contrato en docs/API-PRODUCTOS-GRUPOS.md.
const fuente = {
    listar: () => coreApi('/categorias'),
    actualizar: (id, datos) => coreApi(`/categorias/${id}`, { method: 'PUT', body: datos }),
};

let grupos = [];
let modalGrupo;

async function iniciar() {
    renderLayout(sesion);
    modalGrupo = new bootstrap.Modal('#modal-grupo');
    document.getElementById('form-grupo').addEventListener('submit', guardarGrupo);
    await cargarGrupos();
}

async function cargarGrupos() {
    try {
        grupos = await fuente.listar();
        renderTabla();
    } catch (e) {
        document.getElementById('tabla-grupos').innerHTML =
            '<tr><td colspan="4" class="text-center text-muted">No se pudieron cargar los grupos.</td></tr>';
        mostrarMensaje(`No se pudieron cargar los grupos: ${e.message}`, 'danger');
    }
}

/** 0.10 → "10%"; sin decimales de más. */
function formatoPorcentaje(umbral) {
    return `${Number((umbral * 100).toFixed(1))}%`;
}

function renderTabla() {
    const cuerpo = document.getElementById('tabla-grupos');
    if (!grupos.length) {
        cuerpo.innerHTML = '<tr><td colspan="4" class="text-center text-muted">Todavía no hay grupos cargados.</td></tr>';
        return;
    }
    cuerpo.innerHTML = grupos.map(g => `
        <tr>
            <td class="tw-font-semibold">${escaparHtml(g.nombre)}</td>
            <td class="text-end">${g.diasAlertaVencimiento ?? '-'} días antes</td>
            <td class="text-end">${g.umbralRotacion != null ? formatoPorcentaje(g.umbralRotacion) : '-'}</td>
            <td class="text-end">
                <button class="btn btn-sm btn-outline-secondary" data-id="${g.id}">Editar</button>
            </td>
        </tr>`).join('');
    cuerpo.querySelectorAll('[data-id]').forEach(boton => boton.addEventListener('click', () =>
        abrirModal(grupos.find(g => g.id === Number(boton.dataset.id)))));
}

function abrirModal(grupo) {
    document.getElementById('grupo-id').value = grupo.id;
    document.getElementById('grupo-nombre').value = grupo.nombre;
    document.getElementById('grupo-dias').value = grupo.diasAlertaVencimiento ?? 60;
    document.getElementById('grupo-umbral').value = Number(((grupo.umbralRotacion ?? 0.10) * 100).toFixed(1));
    modalGrupo.show();
}

async function guardarGrupo(evento) {
    evento.preventDefault();
    const id = document.getElementById('grupo-id').value;
    // Se mandan siempre las dos reglas: si faltan, core las vuelve a los valores por defecto (#16).
    const datos = {
        nombre: document.getElementById('grupo-nombre').value.trim(),
        diasAlertaVencimiento: Number(document.getElementById('grupo-dias').value),
        umbralRotacion: Number((Number(document.getElementById('grupo-umbral').value) / 100).toFixed(4)),
    };
    try {
        await fuente.actualizar(id, datos);
        modalGrupo.hide();
        mostrarMensaje(`Grupo ${datos.nombre} actualizado`);
        await cargarGrupos();
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
    }
}

if (sesion) iniciar();
