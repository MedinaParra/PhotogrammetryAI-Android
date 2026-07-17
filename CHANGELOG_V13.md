# v0.13 — Geometric vocabulary and assembly grammar

- Added 20+ primitive and compound geometry classes.
- Added an industrial geometry grammar for pulley imagery.
- Added relations: coaxiality, concentricity, containment, support, attachment and radial repetition.
- Added proposal ranking for real-time UI overlays.
- Expanded the five-image training annotations with shape tags and compound-part decompositions.
- Added a Java smoke test for the local inference grammar.

This is a rule-based/domain-prior expansion, not a newly trained neural network. The five images are useful for validation and annotation design but are not sufficient by themselves to train a robust detector.
