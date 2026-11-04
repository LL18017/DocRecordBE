package ues.edu.sv.education.service;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportService {

    /**
     * Version corta, para el correo que se manda al emitir (solo trae lo
     * basico). El PDF completo de HU-22 -junta, clinica, via- se arma con la
     * sobrecarga de parametros.
     */
    public byte[] generarPreescripcionPdf(
            String nombrePaciente,
            String nombreMedico,
            String fecha,
            List<?> medicamentos
    ) {
        Map<String, Object> parametros = new HashMap<>();
        parametros.put("nombrePaciente", nombrePaciente);
        parametros.put("nombreMedico", nombreMedico);
        parametros.put("fecha", fecha);
        return generarPreescripcionPdf(parametros, medicamentos);
    }

    /**
     * La receta impresa. Los parametros que la plantilla declara y no vengan
     * en el mapa salen en blanco (nombrePaciente, nombreMedico, fecha,
     * expediente, registroJunta, fechaConsulta, clinicaNombre,
     * clinicaDireccion, clinicaTelefono, anulada).
     */
    public byte[] generarPreescripcionPdf(Map<String, Object> parametros, List<?> medicamentos) {
        return generar("reportes/nueva-prescripcion.jrxml", parametros, medicamentos,
                "No fue posible generar el PDF de la prescripción");
    }

    /**
     * El expediente del paciente impreso (HU-34 criterio 5). Los renglones
     * son objetos con getSeccion(), getTitulo(), getDetalle() y getMarca(); la
     * plantilla abre una seccion nueva cada vez que cambia getSeccion(), asi
     * que llegan ya en el orden del documento. Parametros: nombrePaciente,
     * expediente, datosPersonales, generadoEn.
     */
    public byte[] generarExpedientePdf(Map<String, Object> parametros, List<?> renglones) {
        return generar("reportes/expediente-del-paciente.jrxml", parametros, renglones,
                "No fue posible generar el PDF del expediente");
    }

    // Las dos plantillas se compilan al vuelo, como hasta ahora la receta: son
    // documentos que se piden de a uno, y compilar en el arranque obligaria a
    // cargar Jasper aunque nadie imprima nada.
    private byte[] generar(String ruta, Map<String, Object> parametros, List<?> datos, String mensajeDeError) {
        try (InputStream plantilla = new ClassPathResource(ruta).getInputStream()) {

            JasperReport reporte =
                    JasperCompileManager.compileReport(plantilla);

            JRBeanCollectionDataSource fuente =
                    new JRBeanCollectionDataSource(datos);

            JasperPrint documento = JasperFillManager.fillReport(
                    reporte,
                    parametros,
                    fuente
            );

            return JasperExportManager.exportReportToPdf(documento);

        } catch (Exception e) {
            throw new IllegalStateException(mensajeDeError, e);
        }
    }
}
