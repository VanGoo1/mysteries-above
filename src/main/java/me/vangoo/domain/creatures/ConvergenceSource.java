package me.vangoo.domain.creatures;

/**
 * Резонансне джерело, що тяжіє до найближчого кревного Beyonder'а: впала Характеристика, рештка
 * або міфічна істота. Ключоване шляхом+послідовністю.
 *
 * @param pathway  назва шляху джерела (порівняння без регістру)
 * @param group    назва PathwayGroup джерела
 * @param sequence послідовність джерела
 * @param x,z      горизонтальні координати
 */
public record ConvergenceSource(String pathway, String group, int sequence, double x, double z) {}
