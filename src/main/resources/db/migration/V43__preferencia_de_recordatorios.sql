-- ----------------------------------------------------------------------------
-- HU-33 c4 / HU-14 c4 (DRS-101) · DEF-07: "Mi perfil" del paciente
--
-- El paciente decide desde su portal si quiere el recordatorio automatico de
-- cita (unas 24 horas antes, EventProcessorService.recordarCitas). Va en
-- pacientes y no en users: la preferencia es de la persona atendida y el
-- recordatorio se manda al correo de su ficha, tenga o no cuenta en el portal.
--
-- TRUE por defecto: es el comportamiento que todos tenian hasta ahora, asi que
-- ningun paciente existente deja de recibir avisos por esta migracion.
--
-- Solo afecta al RECORDATORIO. La confirmacion al agendar y los avisos de
-- cancelacion o reprogramacion se siguen enviando: son avisos de algo que paso,
-- no recordatorios.
-- ----------------------------------------------------------------------------
ALTER TABLE public.pacientes
    ADD COLUMN recibe_recordatorios BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN public.pacientes.recibe_recordatorios IS
    'Si el paciente quiere el recordatorio automatico de cita (HU-33 c4). Lo cambia el paciente desde /api/mi-perfil.';
