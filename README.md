# sbt-hepek
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/ba.sake/sbt-hepek/badge.svg)](https://maven-badges.herokuapp.com/maven-central/ba.sake/sbt-hepek)  

An sbt plugin for writing Scala `object`s to files.  
See also [**hepek**](https://github.com/sake92/hepek), static content generator that builds upon this plugin.


## Installation

Add the plugin to `project/plugins.sbt`:
```scala
addSbtPlugin("ba.sake" % "sbt-hepek" % "0.4.0")
```

and enable it in `build.sbt`: 
```scala
myProject.enablePlugins(HepekPlugin)
```

## Usage

The main task of sbt-hepek is `hepek`.  
When executed, it will:
1. copy all files from `src/main/resources/public` to `hepek_output` folder
1. write all `object .. extends Renderable` from the `files` package to `hepek_output` folder



Minimal example:

```scala
package files // mandatory !!

import java.nio.file.Paths
import ba.sake.hepek.core.Renderable

object RenderMe extends Renderable {

  override def render =
    "Some text" // arbitrary Scala code
  
  override def relPath = 
    Paths.get("renderme.txt")
}
```

When you run `sbt hepek`, you'll find the `hepek_output/renderme.txt` file,  
with text `Some text`.

## Examples / docs
- [my blog](https://github.com/sake92/sake-ba-source) rendered @ [blog.sake.ba](https://blog.sake.ba)
- [examples](https://github.com/sake92/hepek-examples)
- [philosophy behind hepek](https://dev.to/sake_92/render-static-site-from-scala-code)


---

## Fun fact
I think that this is the first project that tried this approach, namely, using first-class Scala `object`s for this kind of stuff.  
Correct me if I'm wrong... ^_^

---


## About the name

A "hepek" in Bosnian language is a jargon for a thing/thingy/stuff...  
It is used when we don't know the name of a thing: "Give me that ... *hepek*".  
Also, it is used in the famous show called "Top lista nadrealista" as a name for an advanced device which calms down situations of various kinds.  
