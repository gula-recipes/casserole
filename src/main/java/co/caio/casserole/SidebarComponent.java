package co.caio.casserole;

import co.caio.cerberus.model.Diet;
import co.caio.cerberus.model.FacetData;
import co.caio.cerberus.model.FacetData.LabelData;
import co.caio.cerberus.model.SearchQuery;
import co.caio.cerberus.model.SearchQuery.RangedSpec;
import co.caio.cerberus.model.SearchQuery.SortOrder;
import co.caio.cerberus.model.SearchResult;
import co.caio.tablier.model.FilterInfo;
import co.caio.tablier.model.FilterInfo.FilterOption;
import co.caio.tablier.model.SidebarInfo;
import java.util.List;
import java.util.Set;
import org.springframework.web.util.UriComponentsBuilder;

class SidebarComponent {

  static final String SORT_INFO_NAME = "Sort recipes by";
  static final String DIETS_INFO_NAME = "Restrict by Diet";
  static final String INGREDIENTS_INFO_NAME = "Limit Ingredients";
  static final String TIME_INFO_NAME = "Limit Total Time";
  static final String NUTRITION_INFO_NAME = "Limit Nutrition (per serving)";

  private static final SearchResult EMPTY_SEARCH_RESULT = new SearchResult.Builder().build();
  private static final FacetData EMPTY_FACED_DATA =
      new FacetData.Builder().dimension("EMPTY").build();
  private static final LabelData EMPTY_LABEL_DATA = LabelData.of("EMPTY", 0);

  SidebarComponent() {}

  SidebarInfo build(SearchQuery query, UriComponentsBuilder uriBuilder) {
    return build(query, EMPTY_SEARCH_RESULT, uriBuilder);
  }

  SidebarInfo build(SearchQuery query, SearchResult result, UriComponentsBuilder uriBuilder) {
    var builder = new SidebarInfo.Builder();

    addSortOptions(builder, query, uriBuilder.cloneBuilder());
    addDietFilters(builder, query, uriBuilder.cloneBuilder(), getFacetData(result, "diet"));
    addIngredientFilters(
        builder, query, uriBuilder.cloneBuilder(), getFacetData(result, "num_ingredient"));
    addTotalTimeFilters(
        builder, query, uriBuilder.cloneBuilder(), getFacetData(result, "total_time"));
    addNutritionFilters(
        builder, query, uriBuilder.cloneBuilder(), getFacetData(result, "nutrition"));

    return builder.build();
  }

  // XXX Maybe change the result model to use hashmaps for the label
  //     data since the order is irrelevant (or rather, the order is
  //     only important when rendering)

  // TODO expose cerberus.search.IndexField so that I don't need to
  //      hardcode the key param for getFacetData()

  // TODO figure out how to match LabelData

  private FacetData getFacetData(SearchResult result, String key) {
    return result
        .facets()
        .stream()
        .filter(m -> m.dimension().equals(key))
        .findFirst()
        .orElse(EMPTY_FACED_DATA);
  }

  private int countLabelData(FacetData fd, String label) {
    return (int)
        fd.children()
            .stream()
            .filter(m -> m.label().equals(label))
            .findFirst()
            .orElse(EMPTY_LABEL_DATA)
            .count();
  }

  private void addSortOptions(
      SidebarInfo.Builder builder, SearchQuery query, UriComponentsBuilder uriBuilder) {

    var sortInfoBuilder = new FilterInfo.Builder().isRemovable(false).name(SORT_INFO_NAME);

    for (SortOptionSpec spec : sortOptions) {
      sortInfoBuilder.addOptions(spec.buildOption(uriBuilder, query.sort()));
    }

    builder.addFilters(sortInfoBuilder.build());
  }

  private void addDietFilters(
      SidebarInfo.Builder builder,
      SearchQuery query,
      UriComponentsBuilder uriBuilder,
      FacetData fd) {
    var selectedDiets = query.dietThreshold().keySet();

    if (selectedDiets.size() > 1) {
      throw new IllegalStateException("Don't know how to handle multiple selected diets");
    }

    var dietsFilterInfoBuilder = new FilterInfo.Builder().name(DIETS_INFO_NAME);

    if (!fd.children().isEmpty()) {
      dietsFilterInfoBuilder.showCounts(true);
    }

    for (var spec : dietFilterOptions) {
      dietsFilterInfoBuilder.addOptions(
          spec.buildOption(uriBuilder, selectedDiets, countLabelData(fd, spec.queryValue)));
    }

    builder.addFilters(dietsFilterInfoBuilder.build());
  }

  private void addIngredientFilters(
      SidebarInfo.Builder builder,
      SearchQuery query,
      UriComponentsBuilder uriBuilder,
      FacetData fd) {
    var activeIng = query.numIngredients().orElse(unselectedRange);

    var ingredientsFilterInfoBuilder = new FilterInfo.Builder().name(INGREDIENTS_INFO_NAME);

    if (!fd.children().isEmpty()) {
      ingredientsFilterInfoBuilder.showCounts(true);
    }

    for (var spec : ingredientFilterOptions) {
      ingredientsFilterInfoBuilder.addOptions(
          spec.buildOption(uriBuilder, activeIng, countLabelData(fd, spec.queryValue)));
    }

    builder.addFilters(ingredientsFilterInfoBuilder.build());
  }

  private void addTotalTimeFilters(
      SidebarInfo.Builder builder,
      SearchQuery query,
      UriComponentsBuilder uriBuilder,
      FacetData fd) {
    var activeTT = query.totalTime().orElse(unselectedRange);
    var timeFilterInfoBuilder = new FilterInfo.Builder().name(TIME_INFO_NAME);

    for (var spec : totalTimeFilterOptions) {
      timeFilterInfoBuilder.addOptions(
          spec.buildOption(uriBuilder, activeTT, countLabelData(fd, spec.queryValue)));
    }

    builder.addFilters(timeFilterInfoBuilder.build());
  }

  private void addNutritionFilters(
      SidebarInfo.Builder builder,
      SearchQuery query,
      UriComponentsBuilder uriBuilder,
      FacetData fd) {
    var activeKcal = query.calories().orElse(unselectedRange);
    var activeFat = query.fatContent().orElse(unselectedRange);
    var activeCarbs = query.carbohydrateContent().orElse(unselectedRange);

    var nutritionFilterInfoBuilder = new FilterInfo.Builder().name(NUTRITION_INFO_NAME);

    var otherUriBuilder = uriBuilder.cloneBuilder();
    for (var spec : caloriesFilterOptions) {
      nutritionFilterInfoBuilder.addOptions(
          spec.buildOption(otherUriBuilder, activeKcal, countLabelData(fd, spec.queryValue)));
    }

    otherUriBuilder = uriBuilder.cloneBuilder();
    for (var spec : fatFilterOptions) {
      nutritionFilterInfoBuilder.addOptions(
          spec.buildOption(otherUriBuilder, activeFat, countLabelData(fd, spec.queryValue)));
    }

    // NOTE that this uses uriBuilder directly instead of a clone to save a copy
    //      if more options are added this will need to be adjusted
    for (var spec : carbsFilterOptions) {
      nutritionFilterInfoBuilder.addOptions(
          spec.buildOption(uriBuilder, activeCarbs, countLabelData(fd, spec.queryValue)));
    }

    builder.addFilters(nutritionFilterInfoBuilder.build());
  }

  static class SortOptionSpec {
    private final String name;
    private final SortOrder order;
    private final String queryValue;

    SortOptionSpec(String name, SortOrder order, String queryValue) {
      this.name = name;
      this.order = order;
      this.queryValue = queryValue;
    }

    FilterOption buildOption(UriComponentsBuilder uriBuilder, SortOrder active) {
      var href = uriBuilder.replaceQueryParam("sort", queryValue);

      return new FilterInfo.FilterOption.Builder()
          .name(name)
          .isActive(active.equals(order))
          .href(href.build().toUriString())
          .build();
    }
  }

  static class StringOptionSpec {

    private final String name;
    private final String queryName;
    private final String queryValue;

    StringOptionSpec(String name, String queryName, String queryValue) {
      this.name = name;
      this.queryName = queryName;
      this.queryValue = queryValue;
    }

    FilterOption buildOption(UriComponentsBuilder uriBuilder, Set<String> selected, int count) {
      var isActive = selected.contains(queryValue);

      var href =
          isActive
              // XXX improve: hardcoded "science" parameter
              // Make sure the additional parameter is removed as well
              ? uriBuilder.replaceQueryParam(queryName).replaceQueryParam("science")
              : uriBuilder.replaceQueryParam(queryName, queryValue);

      return new FilterInfo.FilterOption.Builder()
          .name(name)
          .count(count)
          .href(href.build().toUriString())
          .isActive(isActive)
          .build();
    }
  }

  static class RangeOptionSpec {

    private final String name;
    private final int start;
    private final int end;
    private final String queryName;
    private final String queryValue;

    RangeOptionSpec(String name, int start, int end, String queryName) {
      this.name = name;
      this.start = start;
      this.end = end;
      this.queryName = queryName;
      this.queryValue = String.format("%d,%d", start, end == Integer.MAX_VALUE ? 0 : end);
    }

    FilterOption buildOption(UriComponentsBuilder uriBuilder, RangedSpec selected, int count) {
      var isActive = selected.start() == start && selected.end() == end;

      var href =
          isActive
              ? uriBuilder.replaceQueryParam(queryName)
              : uriBuilder.replaceQueryParam(queryName, queryValue);

      return new FilterInfo.FilterOption.Builder()
          .name(name)
          .isActive(isActive)
          .count(count)
          .href(href.build().toUriString())
          .build();
    }
  }

  static {
    var sorts =
        List.of(
            new SortOptionSpec("Relevance", SortOrder.RELEVANCE, "relevance"),
            new SortOptionSpec("Fastest to Cook", SortOrder.TOTAL_TIME, "total_time"),
            new SortOptionSpec("Least Ingredients", SortOrder.NUM_INGREDIENTS, "num_ingredients"),
            new SortOptionSpec("Calories", SortOrder.CALORIES, "calories"));
    // Make sure we don't generate URIs with unrecognizable sort order
    sorts.forEach(o -> SortOrder.valueOf(o.queryValue.toUpperCase()));
    sortOptions = sorts;

    var diets =
        List.of(
            new StringOptionSpec("Low Carb", "diet", "lowcarb"),
            new StringOptionSpec("Vegetarian", "diet", "vegetarian"),
            new StringOptionSpec("Keto", "diet", "keto"),
            new StringOptionSpec("Paleo", "diet", "paleo"));

    // Make sure we don't generate URIs with unrecognizable diet name
    diets.forEach(
        d -> {
          assert (Diet.isKnown(d.queryValue));
        });
    dietFilterOptions = diets;
  }

  private static final List<SortOptionSpec> sortOptions;

  private static final List<StringOptionSpec> dietFilterOptions;

  private static final List<RangeOptionSpec> ingredientFilterOptions =
      List.of(
          new RangeOptionSpec("Up to 5", 0, 5, "ni"),
          new RangeOptionSpec("From 6 to 10", 6, 10, "ni"),
          new RangeOptionSpec("More than 10", 10, Integer.MAX_VALUE, "ni"));

  private static final List<RangeOptionSpec> totalTimeFilterOptions =
      List.of(
          new RangeOptionSpec("Up to 15 minutes", 0, 15, "tt"),
          new RangeOptionSpec("From 15 to 30 minutes", 15, 30, "tt"),
          new RangeOptionSpec("From 30 to 60 minutes", 30, 60, "tt"),
          new RangeOptionSpec("One hour or more", 60, Integer.MAX_VALUE, "tt"));

  private static final List<RangeOptionSpec> caloriesFilterOptions =
      List.of(
          new RangeOptionSpec("Up to 200 kcal", 0, 200, "n_k"),
          new RangeOptionSpec("Up to 500 kcal", 0, 500, "n_k"));

  private static final List<RangeOptionSpec> fatFilterOptions =
      List.of(new RangeOptionSpec("Up to 10g of Fat", 0, 10, "n_f"));

  private static final List<RangeOptionSpec> carbsFilterOptions =
      List.of(new RangeOptionSpec("Up to 30g of Carbs", 0, 30, "n_c"));

  private static final RangedSpec unselectedRange = RangedSpec.of(0, 0);
}
