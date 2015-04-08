-- Object Name: ACTIVE_STRUCTURE_ROUTINGS
-- Component Name: MFGSTD

select
  psh.contract,
  psh.part_no,
  psh.bom_type_db,
  psh.eng_chg_level,
  psa.alternative_no structure_alternative,
  rh.routing_revision,
  ra.alternative_no routing_alternative,
  greatest(psh.eff_phase_in_date, rh.phase_in_date) phase_in_date,
  least(
    nvl(psh.eff_phase_out_date, to_date('9999-12-31', 'yyyy-mm-dd')),
    nvl(rh.phase_out_date, to_date('9999-12-31', 'yyyy-mm-dd'))
  ) phase_out_date,
  psa.objstate structure_status,
  ra.objstate routing_status
from ifsapp.prod_structure_head psh
join ifsapp.prod_struct_alternate psa
  on psh.contract = psa.contract
  and psh.part_no = psa.part_no
  and psh.eng_chg_level = psa.eng_chg_level
  and psh.bom_type_db = psa.bom_type_db
  and psa.objstate in ('Tentative', 'Plannable', 'Buildable')
  and exists (
    select
      *
    from ifsapp.prod_structure ps
    where psa.contract = ps.contract
      and psa.part_no = ps.part_no
      and psa.bom_type_db = ps.bom_type_db
      and psa.eng_chg_level = ps.eng_chg_level
      and psa.alternative_no = ps.alternative_no
  )
--
left outer join ifsapp.routing_head rh
  on psh.contract = rh.contract
  and psh.part_no = rh.part_no
  and psh.bom_type_db = rh.bom_type_db
  and rh.phase_in_date <= nvl(psh.eff_phase_out_date, to_date('9999-12-31', 'yyyy-mm-dd'))
  and (
    rh.phase_out_date is null or
    rh.phase_out_date > greatest(sysdate, psh.eff_phase_in_date)
  )
left outer join ifsapp.routing_alternate ra
  on rh.contract = ra.contract
  and rh.part_no = ra.part_no
  and rh.routing_revision = ra.routing_revision
  and rh.bom_type_db = ra.bom_type_db
  and ra.objstate in ('Tentative', 'Plannable', 'Buildable')
  and exists (
    select
      *
    from ifsapp.routing_operation ro
    where ra.contract = ro.contract
      and ra.part_no = ro.part_no
      and ra.bom_type_db = ro.bom_type_db
      and ra.routing_revision = ro.routing_revision
      and ra.alternative_no = ro.alternative_no
  )
--
where trunc(sysdate) between
    psh.eff_phase_in_date and
    nvl(psh.eff_phase_out_date, to_date('9999-12-31', 'yyyy-mm-dd'))
  and (
    psh.bom_type_db = 'P' or
    rh.routing_revision is not null and
    ra.alternative_no is not null
  )
