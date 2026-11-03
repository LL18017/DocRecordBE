package ues.edu.sv.education.model.enums;

/**
 * Estado de una consulta medica.
 *
 * NO lo declara el cliente: lo deriva ConsultaService del propio diagnostico.
 * Una consulta esta PENDIENTE mientras el medico no haya escrito su
 * diagnostico, y FINALIZADA en cuanto lo escribe. Se modela asi -- derivado y
 * no declarado -- porque el estado seria mentira en cuanto alguien pudiera
 * marcar "FINALIZADA" una consulta sin diagnostico: el expediente diria que el
 * paciente fue atendido y no habria ni una linea de lo que se le encontro.
 *
 * Como consecuencia, y esto es deliberado, solo un medico puede cerrar una
 * consulta: cerrarla es escribir el diagnostico, y el diagnostico es
 * exclusivo del medico (ver ConsultaService).
 *
 * ANULADA es la excepcion: no sale del diagnostico sino de un acto explicito
 * (HU-21, ver AnulacionService). Una consulta anulada sigue en el expediente,
 * marcada, y ya no se edita ni recibe recetas. No vuelve a ningun otro estado.
 */
public enum EstadoConsulta {
    PENDIENTE,
    FINALIZADA,
    ANULADA
}
