// Recuerda qué se abrió último (por ejemplo, el id del proveedor en el detalle) para ignorar
// las respuestas que llegan tarde, cuando se abren dos cosas seguidas y responden fuera de orden.

function crearVigente() {
    let actual = null;
    return {
        abrir(id) { actual = id; },
        esActual(id) { return actual === id; },
    };
}

if (typeof module !== 'undefined') module.exports = { crearVigente };
