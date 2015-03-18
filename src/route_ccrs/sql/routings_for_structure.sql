select
  contract,
  part_no,
  bom_type_db,
  routing_revision routing_revision_no,
  routing_alternative routing_alternative_no,
  rank() over (
    order by
      routing_revision desc,
      routing_alternative
  ) "rank"
from ifsinfo.active_structure_routings
where contract = :contract
  and part_no = :part_no
  and bom_type_db = :bom_type
  and eng_chg_level = :eng_chg_level
  and structure_alternative = :alternative_no
  and routing_revision is not null
  and routing_alternative is not null
