package org.analyzer.models;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImportDetails {
    public List<SingleImportDetails<Method>> methodList;
    public List<SingleImportDetails<Field>> fieldList;
    public List<SingleImportDetails<Class<?>>> classList;

    public ImportDetails(ImportClassPath importClassPath, Method[] methodList, Field[] fieldList, Class<?>[] classList) {
        this.methodList = Arrays.stream(methodList).map(method -> new SingleImportDetails<>(importClassPath, method)).toList();
        this.fieldList = Arrays.stream(fieldList).map(field -> new SingleImportDetails<>(importClassPath, field)).toList();

        this.classList = new ArrayList<>();
        for (Class<?> clazz : classList) {
            this.classList.add(new SingleImportDetails<>(importClassPath, clazz));
        }
    }

    public ImportDetails() {
        this.methodList = new ArrayList<>();
        this.fieldList = new ArrayList<>();
        this.classList = new ArrayList<>();
    }

    public SingleImportDetails getClass(String className) throws ClassNotFoundException {
        var filteredClassWithoutWildCard = classList.stream().filter(c -> Arrays.stream(c.importObject.getName().replace("$", ".").split("\\.")).toList().getLast().equals(className) && !c.classPath.isWildCard()).toList();
        if (!filteredClassWithoutWildCard.isEmpty()) {
            return filteredClassWithoutWildCard.getFirst();
        }
        var filteredClass = classList.stream().filter(c -> Arrays.stream(c.importObject.getName().split("\\.")).toList().getLast().equals(className)).toList();
        if (filteredClass.isEmpty()) {
            throw new ClassNotFoundException(className);
        }
        return filteredClass.getFirst();
    }

    public SingleImportDetails getField(String fieldName) throws NoSuchFieldException {
        var filteredFieldWithoutWildCard = fieldList.stream().filter(f -> Arrays.stream(f.importObject.getName().split("\\.")).toList().getLast().equals(fieldName) && !f.classPath.isWildCard()).toList();
        if (!filteredFieldWithoutWildCard.isEmpty()) {
            return filteredFieldWithoutWildCard.getFirst();
        }
        var filteredField = fieldList.stream().filter(f -> Arrays.stream(f.importObject.getName().split("\\.")).toList().getLast().equals(fieldName)).toList();
        if (filteredField.isEmpty()) {
            throw new NoSuchFieldException(fieldName);
        }
        return filteredField.getFirst();
    }

    public SingleImportDetails getMethod(String methodName) throws NoSuchMethodException {
        var filteredMethodWithoutWildCard = methodList.stream().filter(m -> Arrays.stream(m.importObject.getName().split("\\.")).toList().getLast().equals(methodName) && !m.classPath.isWildCard()).toList();
        if (!filteredMethodWithoutWildCard.isEmpty()) {
            return filteredMethodWithoutWildCard.getFirst();
        }
        var filteredMethod = methodList.stream().filter(m -> Arrays.stream(m.importObject.getName().split("\\.")).toList().getLast().equals(methodName)).toList();
        if (filteredMethod.isEmpty()) {
            throw new NoSuchMethodException(methodName);
        }
        return filteredMethod.getFirst();
    }
}
