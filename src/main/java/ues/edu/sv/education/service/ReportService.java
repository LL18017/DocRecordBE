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
        try (InputStream plantilla = new ClassPathResource(
                "reportes/nueva-prescripcion.jrxml"
        ).getInputStream()) {

            JasperReport reporte =
                    JasperCompileManager.compileReport(plantilla);

            JRBeanCollectionDataSource datos =
                    new JRBeanCollectionDataSource(medicamentos);

            JasperPrint documento = JasperFillManager.fillReport(
                    reporte,
                    parametros,
                    datos
            );

            return JasperExportManager.exportReportToPdf(documento);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "No fue posible generar el PDF de la prescripción", e
            );
        }
    }
}