// Evita lanzar dos veces la misma carga mientras la primera sigue en curso
// (por ejemplo, doble clic en "Ver más" pidiendo la misma página).

function crearCargaUnica() {
    const enCurso = new Set();
    return {
        /** Ejecuta `tarea` salvo que ya haya una carga con la misma clave en curso. Devuelve si la ejecutó. */
        async ejecutar(clave, tarea) {
            if (enCurso.has(clave)) return false;
            enCurso.add(clave);
            try {
                await tarea();
                return true;
            } finally {
                enCurso.delete(clave);
            }
        },
    };
}

if (typeof module !== 'undefined') module.exports = { crearCargaUnica };
