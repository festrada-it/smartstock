const sesion = Sesion.requerir(['ADMIN']);

async function iniciar() {
    renderLayout(sesion);
    document.getElementById('form-usuario').addEventListener('submit', crearUsuario);
    await Promise.all([cargarUsuarios(), cargarParametros()]);
}

async function cargarUsuarios() {
    const usuarios = await coreApi('/usuarios');
    const cuerpo = document.getElementById('tabla-usuarios');
    cuerpo.innerHTML = usuarios.map(u => `
        <tr class="${u.activo ? '' : 'text-muted'}">
            <td>${escaparHtml(u.nombre)}</td>
            <td>${escaparHtml(u.email)}</td>
            <td>${u.rol === 'ADMIN' ? 'Administrador' : 'Vendedor'}</td>
            <td>${u.activo ? '✅ Activo' : '⛔ Inactivo'}</td>
            <td class="text-end">
                ${u.email === sesion.email ? '' : `
                    <button class="btn btn-sm ${u.activo ? 'btn-outline-danger' : 'btn-outline-success'}"
                            data-id="${u.id}" data-activo="${!u.activo}">${u.activo ? 'Desactivar' : 'Activar'}</button>`}
            </td>
        </tr>`).join('');
    cuerpo.querySelectorAll('[data-id]').forEach(boton => boton.addEventListener('click', async () => {
        try {
            await coreApi(`/usuarios/${boton.dataset.id}/estado?activo=${boton.dataset.activo}`, { method: 'PUT' });
            await cargarUsuarios();
        } catch (e) {
            mostrarMensaje(e.message, 'danger');
        }
    }));
}

async function crearUsuario(evento) {
    evento.preventDefault();
    try {
        await coreApi('/usuarios', {
            method: 'POST',
            body: {
                nombre: document.getElementById('usuario-nombre').value,
                email: document.getElementById('usuario-email').value,
                password: document.getElementById('usuario-password').value,
                rol: document.getElementById('usuario-rol').value,
            },
        });
        evento.target.reset();
        mostrarMensaje('Usuario creado');
        await cargarUsuarios();
    } catch (e) {
        mostrarMensaje(e.message, 'danger');
    }
}

async function cargarParametros() {
    const parametros = await coreApi('/parametros');
    const cuerpo = document.getElementById('tabla-parametros');
    cuerpo.innerHTML = parametros.map(p => `
        <tr>
            <td class="tw-font-mono tw-text-sm">${escaparHtml(p.clave)}</td>
            <td class="tw-text-sm">${escaparHtml(p.descripcion)}</td>
            <td><input class="form-control form-control-sm" type="number" min="1" value="${escaparHtml(p.valor)}" data-clave="${escaparHtml(p.clave)}"></td>
            <td><button class="btn btn-sm btn-outline-secondary" data-guardar="${escaparHtml(p.clave)}">Guardar</button></td>
        </tr>`).join('');
    cuerpo.querySelectorAll('[data-guardar]').forEach(boton => boton.addEventListener('click', async () => {
        const clave = boton.dataset.guardar;
        const valor = cuerpo.querySelector(`[data-clave="${clave}"]`).value;
        try {
            await coreApi(`/parametros/${clave}`, { method: 'PUT', body: { valor } });
            mostrarMensaje(`Parámetro ${clave} actualizado`);
        } catch (e) {
            mostrarMensaje(e.message, 'danger');
        }
    }));
}

if (sesion) iniciar();
