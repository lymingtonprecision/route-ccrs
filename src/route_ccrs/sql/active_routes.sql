WITH route_ids AS (
  SELECT DISTINCT
    asr.contract,
    asr.part_no,
    asr.bom_type_db,
    asr.routing_revision,
    asr.routing_alternative
  FROM ifsinfo.active_structure_routings asr
  WHERE asr.contract = 'LPE'
    AND asr.part_no = :part_no
)
SELECT
  decode(
    ro.bom_type_db,
    'M', 'manufactured',
    'F', 'repair',
    'P', 'purchased',
    null
  ) AS route__type,
  ri.routing_revision AS route__revision,
  ri.routing_alternative AS route__alternative,
  ra.alternative_description AS route__description,
  ro.operation_no AS id,
  op.operation_description AS description,
  round(
    CASE ro.work_center_type
      WHEN 'O' THEN
        ro.setup + ((ro.batch_run_time + ro.buffer) / (ro.efficiency / 100))
      ELSE
        ro.setup + (ro.batch_run_time / (ro.efficiency / 100))
    END
    * 60,
    2
  ) AS touch_time,
  decode(ro.work_center_type, 'O', 0, ro.buffer * 60) AS buffer,
  ro.work_center_no AS work_center,
  wc.description AS work_center_description,
  wc.average_capacity AS hours_per_day,
  decode(
    wc.work_center_code_db,
    'I', 'internal',
    'O', 'external',
    wc.work_center_code_db
  ) AS type,
  wc_ccr.value_text AS potential_ccr
FROM route_ids ri
JOIN table(ifsapp.lpe_routed_operations_api.operations_tbl(
  'routing',
  (
    ri.contract
    || '-' ||
    ri.part_no
    || '-' ||
    ri.bom_type_db
    || '-' ||
    ri.routing_revision
    || '-' ||
    ri.routing_alternative
  )
)) ro
  ON 1=1
--
JOIN ifsapp.routing_alternate ra
  ON ro.contract = ra.contract
  AND ro.part_no = ra.part_no
  AND ro.bom_type_db = ra.bom_type_db
  AND ro.routing_revision = ra.routing_revision
  AND ro.alternative_no = ra.alternative_no
--
JOIN ifsapp.routing_operation op
  ON ro.contract = op.contract
  AND ro.part_no = op.part_no
  AND ro.bom_type_db = op.bom_type_db
  AND ro.routing_revision = op.routing_revision
  AND ro.alternative_no = op.alternative_no
  AND ro.operation_no = op.operation_no
--
JOIN ifsapp.work_center wc
  ON ro.contract = wc.contract
  AND ro.work_center_no = wc.work_center_no
--
LEFT OUTER JOIN ifsapp.technical_object_reference wcor
  ON wcor.lu_name = 'WorkCenter'
  AND wcor.key_value =
    ro.contract ||
    '^' || ro.work_center_no ||
    '^'
LEFT OUTER JOIN ifsapp.technical_specification_both wc_ccr
  on wcor.technical_spec_no = wc_ccr.technical_spec_no
  AND wc_ccr.attribute = 'CCR'
--
ORDER BY
  ri.routing_revision,
  ri.routing_alternative,
  ro.operation_no
